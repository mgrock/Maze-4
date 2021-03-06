import java.util.Arrays;
import java.util.Random;

/**
 * Created by Matt Mancuso on 1/29/2015.
 */
public class Maze {
    public static enum Difficulty {
        EASY(1),
        MEDIUM(5),
        HARD(8),
        EXTREME(10);

        private int difficulty;

        Difficulty(int difficulty) {
            this.difficulty = difficulty;
        }

        public int getValue() {
            return difficulty;
        }
    }
    private long seed = new Random().nextLong();
    private Random random;
    private int difficulty = Difficulty.MEDIUM.getValue();
    private int sizeX = 16;
    private int sizeY = 16;
    private Integer[] startingCoord;
    private Layout[][] layout;
    private Integer[][] correctPath;

    public static void main(String[] args) {
        MazeBuilder builder = new MazeBuilder();

        Maze maze = builder.setSize(25,25).setDifficulty(1).saveCorrectPath(true).build();

        String[][] print = maze.getAsciiMaze("#", ".");

        for (String[] row : print) {
            for (String col : row) {
                System.out.print(col);
            }
            System.out.println();
        }
    }

    public static class MazeBuilder {
        Maze maze = new Maze();
        private int[][] pathGrid; // -1 = unset, >-1 = path number, -2 = backtracked
        private float[][] randomTable;
        private Layout[][] newLayout;
        private int sizeX = maze.sizeX;
        private int sizeY = maze.sizeY;
        private int difficulty = maze.difficulty;
        private int minMoves;
        private int maxSubPathMoves;
        int subPathLevel;
        int initialPath;
        private Integer[] startingCoord;
        private Integer[] subStartCoord;
        private PathList paths;
        private PathList backtrackedPaths;
        private boolean saveCorrectPath = false;

        public MazeBuilder() {
            difficultyCalculations();
        }

        public MazeBuilder saveCorrectPath(boolean save) {
            saveCorrectPath = save;

            return this;
        }

        public MazeBuilder setSeed(long seed) {
            maze.seed = seed;
            maze.layout = null;
            maze.startingCoord = null;
            pathGrid = null;
            paths = null;
            backtrackedPaths = null;

            return this;
        }

        public MazeBuilder setSize(int sizeX, int sizeY) {
            maze.sizeX = sizeX;
            maze.sizeY = sizeY;
            maze.layout = null;
            maze.startingCoord = null;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            pathGrid = null;
            paths = null;
            backtrackedPaths = null;

            return this;
        }

        public MazeBuilder setDifficulty(int difficulty) {
            if (difficulty < 1 || difficulty > 10) throw new IllegalArgumentException("Difficulty must be between 1 and 10.");
            maze.difficulty = difficulty;
            maze.layout = null;
            maze.startingCoord = null;
            this.difficulty = difficulty;
            pathGrid = null;
            paths = null;
            backtrackedPaths = null;
            difficultyCalculations();

            return this;
        }

        public MazeBuilder setDifficulty(Difficulty difficulty) {
            return setDifficulty(difficulty.getValue());
        }

        public Maze build() {
            Integer[] coord1;
            Integer[] coord2;
            subPathLevel = 0;
            initialPath = 0;

            maze.random = new Random(maze.seed);
            paths = new PathList();
            backtrackedPaths = new PathList().addPath();

            pathGrid = new int[sizeY][sizeX];
            randomTable = new float[sizeY][sizeX];
            newLayout = new Layout[sizeY][sizeX];

            /* Random variables must only be defined in this chunk to prevent
               regeneration issues with previous seeds. Calls to maze.random
               must also be in the same order, i.e. add to the end for new
               variables.
             */
            // BEGIN RANDOM CHUNK //
            startingCoord = new Integer[] {maze.random.nextInt(sizeX), 0};

            for (int row=0; row < sizeY; row++) {
                for (int col=0; col < sizeX; col++) {
                    randomTable[row][col] = maze.random.nextFloat();
                }
            }
            // END RANDOM CHUNK //

            for (int i=0; i<sizeY; i++) {
                Arrays.fill(pathGrid[i], -1);

                for (int n=0; n<sizeX; n++) {
                    newLayout[i][n] = new Layout();
                }
            }

            buildInitialPath();

            buildSubPaths();

            for (int path=0; path<paths.numPaths(); path++) {
                paths.setPath(path);

                for (int coordIndex=0; coordIndex<paths.numCoords(); coordIndex++) {
                    paths.setCoordIndex(coordIndex);
                    coord1 = paths.getCoord();

                    markNextOpen();

                    if (paths.getSubPathLevel() > 0 && coordIndex == 0) {
                        paths.getParentPath().nextCoord();
                        coord2 = paths.getCoord();

                        newLayout[coord1[1]][coord1[0]].setBorder(calculateBorder(coord1, coord2), Layout.Type.OPEN);
                        newLayout[coord2[1]][coord2[0]].setBorder(calculateBorder(coord2, coord1), Layout.Type.OPEN);

                        paths.setPath(path);
                        paths.setCoordIndex(coordIndex);
                    }

                    if (path == 0 && coordIndex == 0) {
                        newLayout[coord1[1]][coord1[0]].setBorder(nearestBoundary(), Layout.Type.ENTRANCE);
                    }
                    if (path == 0 && coordIndex == paths.numCoords() - 1) {
                        newLayout[coord1[1]][coord1[0]].setBorder(nearestBoundary(), Layout.Type.EXIT);
                    }
                }
            }

            maze.startingCoord = startingCoord;
            maze.layout = newLayout;

            if (saveCorrectPath) {
                paths.setPath(0);
                maze.correctPath = new Integer[paths.numCoords()][2];

                for (int i = 0; i < paths.numCoords(); i++) {
                    maze.correctPath[i] = paths.setCoordIndex(i).getCoord();
                }
            }

            return maze;
        }

        private void markNextOpen() {
            int coordIndex1 = paths.getCoordIndex();
            int coordIndex2 = coordIndex1 + 1;
            Integer[] coord1 = paths.getCoord();
            Integer[] coord2;
            Layout.Border border;
            Layout.Border opposite;

            if (coordIndex2 < paths.numCoords()) {
                coord2 = paths.getCoord(coordIndex2);
                border = calculateBorder(coord1, coord2);
                opposite = oppositeBorder(border);

                newLayout[coord1[1]][coord1[0]].setBorder(border, Layout.Type.OPEN);
                newLayout[coord2[1]][coord2[0]].setBorder(opposite, Layout.Type.OPEN);
            }
        }

        private Layout.Border oppositeBorder(Layout.Border border) {
            switch (border) {
                case TOP:
                    return Layout.Border.BOTTOM;
                case BOTTOM:
                    return Layout.Border.TOP;
                case LEFT:
                    return Layout.Border.RIGHT;
                case RIGHT:
                    return Layout.Border.LEFT;
            }

            throw new IllegalArgumentException();
        }

        private Layout.Border calculateBorder(Integer[] coord1, Integer[] coord2) {
            if (coord1[1] == coord2[1] + 1) {
                return Layout.Border.TOP;
            } else if (coord1[1] == coord2[1] - 1) {
                return Layout.Border.BOTTOM;
            } else if (coord1[0] == coord2[0] + 1) {
                return Layout.Border.LEFT;
            } else if (coord1[0] == coord2[0] - 1) {
                return Layout.Border.RIGHT;
            }

            throw new IllegalArgumentException("Coordinates either same or more than one away.");
        }

        private Layout.Border nearestBoundary() {
            if (paths.getCoord()[1] - 1 < 0) {
                return Layout.Border.TOP;
            } else if (paths.getCoord()[1] + 1 >= sizeY) {
                return Layout.Border.BOTTOM;
            } else if (paths.getCoord()[0] - 1 < 0) {
                return Layout.Border.LEFT;
            } else if (paths.getCoord()[0] + 1 >= sizeX) {
                return Layout.Border.RIGHT;
            }

            throw new IllegalStateException("Coordinate not on a bound.");
        }

        private void buildInitialPath() {
            paths.addPath().addCoord(startingCoord);
            setPathGrid();

            while (mustContinuePath()) {
                continuePath(true);
            }

            while (backtrackedPaths.numCoords() > 0) {
                setPathGrid(backtrackedPaths, -1);
                backtrackedPaths.removeCoord();
            }
        }

        private void buildSubPaths() {
            while (!pathGridFull()) {
                paths.setCoordIndex(0);

                while (!pathExhausted()) {
                    subStartCoord = findGreatestAdjacent();

                    if (subStartCoord == null) {
                        paths.nextCoord();
                        continue;
                    }

                    paths.addSubPath();
                    paths.addCoord(subStartCoord);
                    setPathGrid();

                    while (paths.numCoords() <= maxSubPathMoves) {
                        if (!continuePath(false)) break;
                    }

                    paths.getParentPath();
                    paths.nextCoord();
                }

                paths.nextPath(subPathLevel);

                if (paths.getPath() == initialPath) {
                    subPathLevel++;
                    paths.nextPath(subPathLevel);
                    initialPath = paths.getPath();
                }
            }
        }

        private boolean pathExhausted() {
            boolean first = true;
            int originalCoord = paths.getCoordIndex();
            paths.setCoordIndex(0);

            while (first || paths.getCoordIndex() != 0) {
                if (first) first = false;

                if (findGreatestAdjacent() != null) {
                    paths.setCoordIndex(originalCoord);
                    return false;
                }

                paths.nextCoord();
            }

            paths.setCoordIndex(originalCoord);
            return true;
        }

        private boolean pathGridFull() {
            for (int[] row : pathGrid) {
                for (int col : row) {
                    if (col < 0) return false;
                }
            }

            return true;
        }

        private boolean mustContinuePath() {
            if (paths.numCoords() < minMoves) return true;
            if (paths.getCoord()[0] == 0 || paths.getCoord()[0] == sizeX - 1) return false;
            if (paths.getCoord()[1] == 0 || paths.getCoord()[1] == sizeY - 1) return false;
            return true;
        }

        private boolean continuePath(boolean backtrack) {
            Integer[] greatestAdjacent = findGreatestAdjacent();

            if (greatestAdjacent != null) {
                paths.addCoord(greatestAdjacent);
                setPathGrid();
            } else if (backtrack) {
                backtrackedPaths.addCoord(paths.getCoord());
                setPathGrid(-2);
                paths.removeCoord();
            } else return false;

            return true;
        }

        private void setPathGrid(PathList pathList, int customIdentifier) {
            pathGrid[pathList.getCoord()[1]][pathList.getCoord()[0]] = customIdentifier;
        }

        private void setPathGrid(int customIdentifier) {
            setPathGrid(paths, customIdentifier);
        }

        private void setPathGrid() {
            setPathGrid(paths, paths.getPath());
        }

        private Integer[] findGreatestAdjacent() {
            Integer[] initialCoord = paths.getCoord();
            Integer[] greatestCoord = null;
            float greatestValue = -1;
            Integer[][] testCoords = new Integer[4][2];

            testCoords[0] = new Integer[] {initialCoord[0], initialCoord[1] - 1}; // Up
            testCoords[1] = new Integer[] {initialCoord[0], initialCoord[1] + 1}; // Down
            testCoords[2] = new Integer[] {initialCoord[0] - 1, initialCoord[1]}; // Left
            testCoords[3] = new Integer[] {initialCoord[0] + 1, initialCoord[1]}; // Right

            for (Integer[] coord : testCoords) {
                if ((coord[0] >= 0 && coord[0] < sizeX)
                        && (coord[1] >= 0 && coord[1] < sizeY)
                        && (pathGrid[coord[1]][coord[0]] == -1)
                        && (randomTable[coord[1]][coord[0]] > greatestValue)) {
                    greatestCoord = coord;
                    greatestValue = randomTable[coord[1]][coord[0]];
                }
            }

            return greatestCoord;
        }

        private void difficultyCalculations() {
            double percentageTotalMain = (0.10+((difficulty/10)*0.15));
            double percentageTotalSub = (0.05+((difficulty/10)*.20));
            int area = sizeX*sizeY;
            minMoves = (int) Math.round(area*percentageTotalMain);
            maxSubPathMoves = (int) Math.round(area*percentageTotalSub);
        }
    }

    public String[][] getAsciiMaze(String printChar, String correctChar) {
        String[][] print;

        print = new String[sizeY*2+1][sizeX*2+1];

        for (int i=0; i<print.length; i++) {
            Arrays.fill(print[i], " ");
        }

        print[0][0] = printChar;

        for (int row=0; row<sizeY; row++) {
            for (int col=0; col<sizeX; col++) {
                if (row == 0) {
                    print[0][1+col*2+1] = printChar;
                    if (layout[row][col].borderIs(Layout.Border.TOP, Layout.Type.WALL)) {
                        print[0][1+col*2] = printChar;
                    }
                }

                if (col == 0) {
                    print[1+row*2+1][0] = printChar;
                    if (layout[row][col].borderIs(Layout.Border.LEFT, Layout.Type.WALL)) {
                        print[1+row*2][0] = printChar;
                    }
                }

                print[1+row*2+1][1+col*2+1] = printChar;
                if (layout[row][col].borderIs(Layout.Border.BOTTOM, Layout.Type.WALL)) {
                    print[1+row*2+1][1+col*2] = printChar;
                }
                if (layout[row][col].borderIs(Layout.Border.RIGHT, Layout.Type.WALL)) {
                    print[1+row*2][1+col*2+1] = printChar;
                }
            }
        }

        if (correctChar != null && correctPath != null) {
            for (Integer[] coord : correctPath) {
                print[1 + coord[1] * 2][1 + coord[0] * 2] = correctChar;
            }
        }

        return print;
    }

    public String[][] getAsciiMaze(String printChar) {
        return getAsciiMaze(printChar, null);
    }

    public String[][] getAsciiMaze() {
        return getAsciiMaze("#");
    }
}