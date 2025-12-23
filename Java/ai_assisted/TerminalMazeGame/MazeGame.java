import java.util.*;
import java.util.concurrent.*;
import java.time.Duration;
import java.time.Instant;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

public class MazeGame {
    // 迷宫尺寸
    private static final int ROWS = 21;
    private static final int COLS = 41;
    
    // 迷宫单元格类型
    private static final char WALL = '#';
    private static final char PATH = ' ';
    private static final char PLAYER = 'P';
    private static final char EXIT = 'E';
    private static final char VISITED = '.';
    private static final char SOLUTION = '*';
    
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
    private boolean gameOver;
    private int moves;
    private int score;
    private Instant startTime;
    private Terminal terminal;
    private NonBlockingReader reader;
    private boolean showSolution;
    private ExecutorService executor;
    
    // 颜色代码 (ANSI转义序列)
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE_BG = "\u001B[47m";
    private static final String BLACK = "\u001B[30m";
    
    public MazeGame() {
        try {
            // 设置终端为原始模式，支持实时输入
            terminal = TerminalBuilder.builder()
                .system(true)
                .jna(true)
                .build();
            
            // 设置终端为原始模式，禁用回显
            terminal.enterRawMode();
            reader = terminal.reader();
            
            maze = new char[ROWS][COLS];
            gameWon = false;
            gameOver = false;
            moves = 0;
            score = 1000;
            showSolution = false;
            
            executor = Executors.newSingleThreadExecutor();
            
        } catch (Exception e) {
            System.err.println("初始化终端失败: " + e.getMessage());
            System.err.println("将使用标准输入模式...");
            // 如果jline3不可用，回退到标准模式
        }
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
        
        // 确保起点和终点连通
        ensureConnectivity();
    }
    
    // 确保起点和终点连通
    private void ensureConnectivity() {
        // 使用BFS检查连通性
        boolean[][] visited = new boolean[ROWS][COLS];
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{playerRow, playerCol});
        visited[playerRow][playerCol] = true;
        
        boolean exitReachable = false;
        
        while (!queue.isEmpty() && !exitReachable) {
            int[] current = queue.poll();
            int row = current[0];
            int col = current[1];
            
            if (row == exitRow && col == exitCol) {
                exitReachable = true;
                break;
            }
            
            for (int[] dir : DIRECTIONS) {
                int newRow = row + dir[0];
                int newCol = col + dir[1];
                
                if (newRow >= 0 && newRow < ROWS && newCol >= 0 && newCol < COLS &&
                    !visited[newRow][newCol] && maze[newRow][newCol] != WALL) {
                    visited[newRow][newCol] = true;
                    queue.add(new int[]{newRow, newCol});
                }
            }
        }
        
        // 如果出口不可达，创建一个路径
        if (!exitReachable) {
            createPathToExit();
        }
    }
    
    // 创建到出口的路径
    private void createPathToExit() {
        // 简单地从玩家位置向右下角创建一条路径
        int currentRow = playerRow;
        int currentCol = playerCol;
        
        while (currentRow < exitRow || currentCol < exitCol) {
            if (currentRow < exitRow) {
                currentRow++;
                if (maze[currentRow][currentCol] == WALL) {
                    maze[currentRow][currentCol] = PATH;
                }
            }
            if (currentCol < exitCol) {
                currentCol++;
                if (maze[currentRow][currentCol] == WALL) {
                    maze[currentRow][currentCol] = PATH;
                }
            }
        }
    }
    
    // 显示迷宫
    public void displayMaze() {
        // 清屏
        System.out.print("\033[H\033[2J");
        System.out.flush();
        
        // 显示标题和游戏信息
        System.out.println(CYAN + "=== 终端迷宫游戏 (实时控制版) ===" + RESET);
        System.out.println(YELLOW + "控制: " + RESET + 
                          GREEN + "方向键/WASD" + RESET + "移动 | " + 
                          RED + "Q" + RESET + "退出 | " + 
                          BLUE + "R" + RESET + "重置 | " + 
                          YELLOW + "T" + RESET + "显示路径");
        
        // 计算游戏时间
        Duration duration = Duration.between(startTime, Instant.now());
        long minutes = duration.toMinutes();
        long seconds = duration.minusMinutes(minutes).getSeconds();
        
        System.out.println(String.format(
            CYAN + "时间: %02d:%02d | 移动: %d | 分数: %d" + RESET,
            minutes, seconds, moves, score
        ));
        
        System.out.println(GREEN + "符号: " + PLAYER + "=玩家, " + 
                          RED + EXIT + RESET + GREEN + "=出口, " + 
                          YELLOW + VISITED + RESET + GREEN + "=已走路径, " + 
                          BLUE + SOLUTION + RESET + GREEN + "=解决方案" + RESET);
        System.out.println();
        
        // 显示迷宫
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                char cell = maze[i][j];
                String coloredCell = getColoredCell(cell, i, j);
                System.out.print(coloredCell);
            }
            System.out.println();
        }
        System.out.println();
        
        // 显示游戏状态
        if (gameWon) {
            System.out.println(GREEN + "🎉 恭喜！你找到了出口！🎉" + RESET);
            System.out.println(YELLOW + "总移动次数: " + moves + RESET);
            System.out.println(YELLOW + "最终得分: " + score + RESET);
            System.out.println(CYAN + "按任意键退出..." + RESET);
        } else if (score <= 0) {
            System.out.println(RED + "💀 游戏结束！分数已归零！💀" + RESET);
            System.out.println(CYAN + "按任意键退出..." + RESET);
        }
    }
    
    // 获取带颜色的单元格
    private String getColoredCell(char cell, int row, int col) {
        switch (cell) {
            case PLAYER:
                return GREEN + PLAYER + RESET;
            case EXIT:
                return RED + EXIT + RESET;
            case VISITED:
                return YELLOW + VISITED + RESET;
            case SOLUTION:
                return BLUE + SOLUTION + RESET;
            case WALL:
                // 为墙壁添加背景色，增强可读性
                return WHITE_BG + BLACK + WALL + RESET;
            default:
                return String.valueOf(cell);
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
            // 到达出口有额外加分
            score += 500;
        }
        
        // 移动玩家
        playerRow = newRow;
        playerCol = newCol;
        maze[playerRow][playerCol] = PLAYER;
        moves++;
        
        // 每移动一次扣一点分数，鼓励高效完成
        score = Math.max(0, score - 1);
        
        return true;
    }
    
    // 重置游戏
    public void resetGame() {
        generateMaze();
        gameWon = false;
        gameOver = false;
        moves = 0;
        score = 1000;
        showSolution = false;
        startTime = Instant.now();
    }
    
    // 显示解决方案
    public void showSolution() {
        if (showSolution) {
            // 如果已经显示，则隐藏
            hideSolution();
            showSolution = false;
            return;
        }
        
        // 使用BFS寻找最短路径
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
                if (maze[row][col] != PLAYER && maze[row][col] != EXIT) {
                    maze[row][col] = SOLUTION;
                }
                int tempRow = parentRow[row][col];
                int tempCol = parentCol[row][col];
                row = tempRow;
                col = tempCol;
            }
            
            showSolution = true;
            // 显示解决方案会扣分
            score = Math.max(0, score - 50);
        }
    }
    
    // 隐藏解决方案
    private void hideSolution() {
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                if (maze[i][j] == SOLUTION) {
                    maze[i][j] = PATH;
                }
            }
        }
    }
    
    // 处理键盘输入
    private void handleInput(int key) {
        boolean moved = false;
        
        switch (key) {
            // 方向键 (上)
            case 65: // 上箭头
            case 'w':
            case 'W':
                moved = movePlayer(-1, 0);
                break;
                
            // 方向键 (下)
            case 66: // 下箭头
            case 's':
            case 'S':
                moved = movePlayer(1, 0);
                break;
                
            // 方向键 (左)
            case 68: // 左箭头
            case 'a':
            case 'A':
                moved = movePlayer(0, -1);
                break;
                
            // 方向键 (右)
            case 67: // 右箭头
            case 'd':
            case 'D':
                moved = movePlayer(0, 1);
                break;
                
            // 重置游戏
            case 'r':
            case 'R':
                resetGame();
                break;
                
            // 显示/隐藏解决方案
            case 't':
            case 'T':
                showSolution();
                break;
                
            // 退出游戏
            case 'q':
            case 'Q':
                gameOver = true;
                break;
                
            // ESC键
            case 27: // ESC
                // 检查是否是方向键的一部分
                if (reader != null) {
                    try {
                        int next1 = reader.read(10); // 读取下一个字符
                        if (next1 == 91) { // '['
                            int next2 = reader.read(10); // 读取方向键
                            handleInput(next2); // 处理方向键
                        } else {
                            gameOver = true; // ESC alone
                        }
                    } catch (Exception e) {
                        gameOver = true;
                    }
                } else {
                    gameOver = true;
                }
                break;
        }
        
        // 如果移动无效，轻微扣分
        if (!moved && (key == 65 || key == 66 || key == 67 || key == 68 || 
                       key == 'w' || key == 'W' || key == 's' || key == 'S' ||
                       key == 'a' || key == 'A' || key == 'd' || key == 'D')) {
            score = Math.max(0, score - 2);
        }
    }
    
    // 游戏主循环
    public void play() {
        startTime = Instant.now();
        resetGame();
        
        System.out.println(CYAN + "游戏开始！使用方向键或WASD移动，找到出口(E)！" + RESET);
        System.out.println(YELLOW + "提示: 按T可以显示最短路径（会扣分）" + RESET);
        System.out.println("正在启动游戏...");
        
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 主游戏循环
        while (!gameWon && !gameOver && score > 0) {
            displayMaze();
            
            try {
                // 非阻塞读取，等待100毫秒
                int key = reader.read(100);
                
                if (key >= 0) { // 有输入
                    handleInput(key);
                }
                
                // 稍微延迟，减少CPU使用
                Thread.sleep(50);
                
            } catch (Exception e) {
                // 忽略超时异常
                if (!(e instanceof InterruptedException)) {
                    System.err.println("输入错误: " + e.getMessage());
                }
            }
        }
        
        // 游戏结束画面
        displayMaze();
        
        // 等待用户按键退出
        if (reader != null) {
            try {
                System.out.println(CYAN + "按任意键退出..." + RESET);
                reader.read();
            } catch (Exception e) {
                // 忽略异常
            }
        }
        
        cleanup();
    }
    
    // 清理资源
    private void cleanup() {
        if (executor != null) {
            executor.shutdown();
        }
        
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception e) {
                // 忽略异常
            }
        }
        
        if (terminal != null) {
            try {
                terminal.close();
            } catch (Exception e) {
                // 忽略异常
            }
        }
    }
    
    // 显示菜单
    public void showMenu() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
        
        System.out.println(CYAN + "╔══════════════════════════════════╗" + RESET);
        System.out.println(CYAN + "║       终端迷宫游戏 - 实时版       ║" + RESET);
        System.out.println(CYAN + "╠══════════════════════════════════╣" + RESET);
        System.out.println(CYAN + "║                                  ║" + RESET);
        System.out.println(CYAN + "║  " + GREEN + "1. 开始新游戏" + CYAN + "                    ║" + RESET);
        System.out.println(CYAN + "║  " + YELLOW + "2. 查看游戏说明" + CYAN + "                  ║" + RESET);
        System.out.println(CYAN + "║  " + RED + "3. 退出游戏" + CYAN + "                       ║" + RESET);
        System.out.println(CYAN + "║                                  ║" + RESET);
        System.out.println(CYAN + "╚══════════════════════════════════╝" + RESET);
        System.out.println();
        System.out.print(CYAN + "请选择 (1-3): " + RESET);
    }
    
    // 显示游戏说明
    public void showInstructions() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
        
        System.out.println(CYAN + "════════════ 游戏说明 ════════════" + RESET);
        System.out.println();
        System.out.println(YELLOW + "目标:" + RESET + " 控制玩家(" + GREEN + "P" + RESET + 
                          ")找到出口(" + RED + "E" + RESET + ")");
        System.out.println();
        System.out.println(YELLOW + "控制方式:" + RESET);
        System.out.println("  • " + GREEN + "方向键" + RESET + " 或 " + GREEN + "WASD" + RESET + " - 移动玩家");
        System.out.println("  • " + RED + "Q" + RESET + " - 退出游戏");
        System.out.println("  • " + BLUE + "R" + RESET + " - 重置游戏（新迷宫）");
        System.out.println("  • " + YELLOW + "T" + RESET + " - 显示/隐藏最短路径（会扣分）");
        System.out.println();
        System.out.println(YELLOW + "游戏规则:" + RESET);
        System.out.println("  • 初始分数: 1000分");
        System.out.println("  • 每移动一步: -1分");
        System.out.println("  • 撞到墙壁: -2分");
        System.out.println("  • 显示路径: -50分");
        System.out.println("  • 到达出口: +500分");
        System.out.println("  • 分数归零: 游戏结束");
        System.out.println();
        System.out.println(CYAN + "按任意键返回主菜单..." + RESET);
        
        try {
            if (reader != null) {
                reader.read();
            } else {
                new Scanner(System.in).nextLine();
            }
        } catch (Exception e) {
            // 忽略异常
        }
    }
    
    public static void main(String[] args) {
        MazeGame game = new MazeGame();
        
        boolean exit = false;
        Scanner scanner = new Scanner(System.in);
        
        while (!exit) {
            game.showMenu();
            
            String choice;
            if (game.reader != null) {
                try {
                    int key = game.reader.read();
                    choice = String.valueOf((char) key);
                } catch (Exception e) {
                    choice = scanner.nextLine();
                }
            } else {
                choice = scanner.nextLine();
            }
            
            switch (choice) {
                case "1":
                    game.play();
                    break;
                case "2":
                    game.showInstructions();
                    break;
                case "3":
                    exit = true;
                    break;
                default:
                    System.out.println(RED + "无效选择，请重新输入！" + RESET);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
            }
        }
        
        game.cleanup();
        scanner.close();
        System.out.println(CYAN + "感谢游玩！再见！" + RESET);
    }
}
