import java.util.*;
import java.util.concurrent.TimeUnit;

public class TerminalMazeGame {
    // 迷宫尺寸
    private static final int ROWS = 21;
    private static final int COLS = 41;
    
    // 迷宫单元格类型
    private static final char WALL = '#';
    private static final char PATH = ' ';
    private static final char PLAYER = 'P';
    private static final char EXIT = 'E';
    private static final char VISITED = '.';
    
    // 方向常量
    private static final int[][] DIRECTIONS = {
        {-1, 0}, // 上
        {1, 0},  // 下
        {0, -1}, // 左
        {0, 1}   // 右
    };
    
    private char[][] maze;
    private int playerRow, playerCol;
    private int exitRow, exitCol;
    private boolean gameWon;
    private int moves;
    private Scanner scanner;
    
    public TerminalMazeGame() {
        maze = new char[ROWS][COLS];
        scanner = new Scanner(System.in);
        gameWon = false;
        moves = 0;
    }
    
    // 生成迷宫
    public void generateMaze() {
        // 初始化迷宫为墙壁
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                maze[i][j] = WALL;
            }
        }
        
        // 使用深度优先搜索生成迷宫
        Random rand = new Random();
        Stack<int[]> stack = new Stack<>();
        
        // 随机选择起始点（确保是奇数行奇数列，使迷宫有边界）
        int startRow = 1;
        int startCol = 1;
        maze[startRow][startCol] = PATH;
        stack.push(new int[]{startRow, startCol});
        
        while (!stack.isEmpty()) {
            int[] current = stack.peek();
            int row = current[0];
            int col = current[1];
            
            // 获取未访问的邻居
            List<int[]> neighbors = new ArrayList<>();
            
            for (int[] dir : DIRECTIONS) {
                int newRow = row + dir[0] * 2;
                int newCol = col + dir[1] * 2;
                
                if (newRow > 0 && newRow < ROWS - 1 && 
                    newCol > 0 && newCol < COLS - 1 && 
                    maze[newRow][newCol] == WALL) {
                    neighbors.add(new int[]{newRow, newCol, dir[0], dir[1]});
                }
            }
            
            if (!neighbors.isEmpty()) {
                // 随机选择一个邻居
                int[] chosen = neighbors.get(rand.nextInt(neighbors.size()));
                int newRow = chosen[0];
                int newCol = chosen[1];
                int dirRow = chosen[2];
                int dirCol = chosen[3];
                
                // 打通墙壁
                maze[row + dirRow][col + dirCol] = PATH;
                maze[newRow][newCol] = PATH;
                
                stack.push(new int[]{newRow, newCol});
            } else {
                stack.pop();
            }
        }
        
        // 设置玩家起始位置
        playerRow = 1;
        playerCol = 1;
        maze[playerRow][playerCol] = PLAYER;
        
        // 设置出口位置（右下角）
        exitRow = ROWS - 2;
        exitCol = COLS - 2;
        maze[exitRow][exitCol] = EXIT;
    }
    
    // 显示迷宫
    public void displayMaze() {
        // 清屏 (WSL终端)
        System.out.print("\033[H\033[2J");
        System.out.flush();
        
        System.out.println("=== 终端迷宫游戏 (WSL版) ===");
        System.out.println("控制: W(上) A(左) S(下) D(右) | Q(退出) | R(重置)");
        System.out.println("移动次数: " + moves);
        System.out.println("符号: " + PLAYER + "=玩家, " + EXIT + "=出口, " + VISITED + "=已走路径");
        System.out.println();
        
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                System.out.print(maze[i][j]);
            }
            System.out.println();
        }
        System.out.println();
        
        if (gameWon) {
            System.out.println("恭喜！你找到了出口！");
            System.out.println("总移动次数: " + moves);
            System.out.println("按任意键退出...");
        }
    }
    
    // 移动玩家
    public boolean movePlayer(int dRow, int dCol) {
        int newRow = playerRow + dRow;
        int newCol = playerCol + dCol;
        
        // 检查边界和墙壁
        if (newRow < 0 || newRow >= ROWS || newCol < 0 || newCol >= COLS) {
            return false;
        }
        
        if (maze[newRow][newCol] == WALL) {
            return false;
        }
        
        // 标记已访问的位置
        if (maze[playerRow][playerCol] == PLAYER) {
            maze[playerRow][playerCol] = VISITED;
        }
        
        // 检查是否到达出口
        if (maze[newRow][newCol] == EXIT) {
            gameWon = true;
        }
        
        // 移动玩家
        playerRow = newRow;
        playerCol = newCol;
        maze[playerRow][playerCol] = PLAYER;
        moves++;
        
        return true;
    }
    
    // 重置游戏
    public void resetGame() {
        generateMaze();
        gameWon = false;
        moves = 0;
    }
    
    // 游戏主循环
    public void play() {
        generateMaze();
        
        while (!gameWon) {
            displayMaze();
            
            System.out.print("输入移动方向 (W/A/S/D): ");
            String input = scanner.nextLine().toLowerCase();
            
            if (input.equals("q")) {
                System.out.println("游戏结束。");
                break;
            }
            
            if (input.equals("r")) {
                resetGame();
                continue;
            }
            
            boolean moved = false;
            switch (input) {
                case "w":
                    moved = movePlayer(-1, 0);
                    break;
                case "s":
                    moved = movePlayer(1, 0);
                    break;
                case "a":
                    moved = movePlayer(0, -1);
                    break;
                case "d":
                    moved = movePlayer(0, 1);
                    break;
                default:
                    System.out.println("无效输入！使用 W/A/S/D 移动，Q 退出，R 重置");
                    try {
                        TimeUnit.MILLISECONDS.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
            }
            
            if (!moved && !input.equals("w") && !input.equals("a") && 
                !input.equals("s") && !input.equals("d")) {
                System.out.println("不能朝那个方向移动！");
                try {
                    TimeUnit.MILLISECONDS.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        if (gameWon) {
            displayMaze();
            scanner.nextLine(); // 等待按键
        }
        
        scanner.close();
    }
    
    // 自动求解演示（可选功能）
    public void autoSolveDemo() {
        System.out.println("\n演示自动求解...");
        resetGame();
        
        // 使用BFS寻找路径
        boolean[][] visited = new boolean[ROWS][COLS];
        int[][] parentRow = new int[ROWS][COLS];
        int[][] parentCol = new int[ROWS][COLS];
        
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{playerRow, playerCol});
        visited[playerRow][playerCol] = true;
        
        // BFS搜索
        boolean found = false;
        while (!queue.isEmpty() && !found) {
            int[] current = queue.poll();
            int row = current[0];
            int col = current[1];
            
            if (row == exitRow && col == exitCol) {
                found = true;
                break;
            }
            
            for (int[] dir : DIRECTIONS) {
                int newRow = row + dir[0];
                int newCol = col + dir[1];
                
                if (newRow >= 0 && newRow < ROWS && newCol >= 0 && newCol < COLS &&
                    !visited[newRow][newCol] && maze[newRow][newCol] != WALL) {
                    visited[newRow][newCol] = true;
                    parentRow[newRow][newCol] = row;
                    parentCol[newRow][newCol] = col;
                    queue.add(new int[]{newRow, newCol});
                }
            }
        }
        
        // 回溯路径
        if (found) {
            int row = exitRow;
            int col = exitCol;
            
            while (row != playerRow || col != playerCol) {
                maze[row][col] = '*'; // 标记路径
                int tempRow = parentRow[row][col];
                int tempCol = parentCol[row][col];
                row = tempRow;
                col = tempCol;
            }
            
            maze[playerRow][playerCol] = PLAYER;
            maze[exitRow][exitCol] = EXIT;
            
            // 显示带路径的迷宫
            System.out.print("\033[H\033[2J");
            System.out.flush();
            System.out.println("=== 迷宫解决方案 (BFS算法) ===");
            System.out.println("符号: " + PLAYER + "=起点, " + EXIT + "=终点, *=路径");
            System.out.println();
            
            for (int i = 0; i < ROWS; i++) {
                for (int j = 0; j < COLS; j++) {
                    System.out.print(maze[i][j]);
                }
                System.out.println();
            }
            
            System.out.println("\n按回车键返回主游戏...");
            scanner.nextLine();
        }
    }
    
    public static void main(String[] args) {
        TerminalMazeGame game = new TerminalMazeGame();
        
        System.out.println("欢迎来到终端迷宫游戏！");
        System.out.println("1. 开始新游戏");
        System.out.println("2. 查看自动求解演示");
        System.out.print("请选择 (1 或 2): ");
        
        Scanner menuScanner = new Scanner(System.in);
        String choice = menuScanner.nextLine();
        
        if (choice.equals("2")) {
            game.autoSolveDemo();
        }
        
        game.play();
        menuScanner.close();
    }
}
