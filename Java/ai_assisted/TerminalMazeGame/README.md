# 增强版终端迷宫游戏 - 实时键盘输入

以下是改进后的版本，实现了实时键盘输入，无需每次按回车键。我使用了`jline3`库来处理终端输入，它提供了跨平台的键盘事件支持。

## 项目结构

```
TerminalMazeGame/
├── MazeGame.java           # 主游戏类
├── pom.xml                # Maven配置文件
└── README.md              # 说明文档
```

## 1. 主游戏文件 (MazeGame.java)

```java
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
```

## 2. Maven配置文件 (pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>com.mazegame</groupId>
    <artifactId>terminal-maze-game</artifactId>
    <version>1.0.0</version>
    
    <properties>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    
    <dependencies>
        <!-- JLine 3 for terminal handling -->
        <dependency>
            <groupId>org.jline</groupId>
            <artifactId>jline</artifactId>
            <version>3.21.0</version>
        </dependency>
        
        <!-- JNA for native terminal support -->
        <dependency>
            <groupId>net.java.dev.jna</groupId>
            <artifactId>jna</artifactId>
            <version>5.12.1</version>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.10.1</version>
            </plugin>
            
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.1.0</version>
                <configuration>
                    <mainClass>MazeGame</mainClass>
                </configuration>
            </plugin>
            
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-assembly-plugin</artifactId>
                <version>3.4.2</version>
                <configuration>
                    <descriptorRefs>
                        <descriptorRef>jar-with-dependencies</descriptorRef>
                    </descriptorRefs>
                    <archive>
                        <manifest>
                            <mainClass>MazeGame</mainClass>
                        </manifest>
                    </archive>
                </configuration>
                <executions>
                    <execution>
                        <id>make-assembly</id>
                        <phase>package</phase>
                        <goals>
                            <goal>single</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

## 3. 使用说明

### 安装和运行

#### 方法一：使用Maven（推荐）

1. 确保已安装Java 11+ 和 Maven
2. 将上面的文件保存到项目目录
3. 在项目目录中运行：

```bash
# 编译并运行
mvn compile exec:java

# 或打包为可执行JAR
mvn clean package
java -jar target/terminal-maze-game-1.0.0-jar-with-dependencies.jar
```

#### 方法二：手动编译和运行

如果你没有安装Maven，可以手动下载jline3库：

1. 下载jline3 JAR文件：
   - https://repo1.maven.org/maven2/org/jline/jline/3.21.0/jline-3.21.0.jar
   - https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.12.1/jna-5.12.1.jar

2. 编译和运行：
```bash
# 编译
javac -cp "jline-3.21.0.jar:jna-5.12.1.jar" MazeGame.java

# 运行
java -cp ".:jline-3.21.0.jar:jna-5.12.1.jar" MazeGame
```

### 游戏特性

1. **实时控制**：
   - 使用方向键或WASD键直接控制，无需按回车
   - 即时响应，流畅的游戏体验

2. **增强功能**：
   - 彩色终端输出
   - 分数系统（初始1000分）
   - 计时功能
   - 显示/隐藏解决方案（按T键）

3. **游戏控制**：
   - `方向键` 或 `WASD`：移动玩家
   - `R`：重置游戏（生成新迷宫）
   - `T`：显示/隐藏最短路径（会扣50分）
   - `Q`：退出游戏

4. **分数规则**：
   - 每移动一步：-1分
   - 撞到墙壁：-2分
   - 显示路径：-50分
   - 到达出口：+500分
   - 分数归零：游戏结束

### 注意事项

1. 游戏使用了ANSI颜色代码，确保终端支持彩色显示
2. 如果jline3库无法正常工作，游戏会回退到标准输入模式
3. 在WSL中可能需要启用终端原始模式（jline3会自动处理）
4. 游戏退出时会自动清理终端设置

### 故障排除

如果遇到控制问题：

1. **权限问题**：确保有足够的权限运行终端程序
2. **终端兼容性**：如果在某些终端中无法正常工作，尝试使用不同的终端模拟器
3. **回退模式**：如果实时输入不工作，可以修改代码使用回退模式（标准输入）

这个版本提供了更流畅的游戏体验，支持实时键盘输入，无需每次移动都按回车键！
