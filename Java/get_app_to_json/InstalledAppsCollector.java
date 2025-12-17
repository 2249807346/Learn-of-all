import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Windows已安装应用程序信息收集工具
 * 排除UWP应用，只收集传统桌面应用
 */
public class InstalledAppsCollector {
    
    // 输出文件名
    private static final String OUTPUT_FILE = "installed_apps.json";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    // 常见的UWP应用特征和需要排除的路径
    private static final List<String> UWP_INDICATORS = Arrays.asList(
        "WindowsApps", "Microsoft.MicrosoftEdge", "Microsoft.Windows", 
        "AppData\\Local\\Packages", "Program Files\\WindowsApps"
    );
    
    // 需要排除的系统组件和通用应用
    private static final List<Pattern> EXCLUDE_PATTERNS = Arrays.asList(
        Pattern.compile("Microsoft Visual C\\+\\+.*Redistributable", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Microsoft .* Framework.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*Windows.*Update.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*Microsoft .* Runtime.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*DirectX.*", Pattern.CASE_INSENSITIVE)
    );
    
    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+8"));
    }
    
    /**
     * 主方法
     */
    public static void main(String[] args) {
        System.out.println("开始收集已安装的应用程序信息...");
        
        try {
            List<AppInfo> installedApps = collectInstalledApps();
            
            // 过滤掉UWP应用和系统组件
            List<AppInfo> filteredApps = filterApps(installedApps);
            
            // 转换为JSON并保存到文件
            String json = convertToJson(filteredApps);
            saveToFile(json);
            
            System.out.println("共找到 " + filteredApps.size() + " 个传统桌面应用程序");
            System.out.println("结果已保存到: " + new File(OUTPUT_FILE).getAbsolutePath());
            
        } catch (Exception e) {
            System.err.println("程序执行出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 收集已安装的应用程序信息
     */
    private static List<AppInfo> collectInstalledApps() {
        List<AppInfo> apps = new ArrayList<>();
        
        // 从注册表获取应用程序信息
        apps.addAll(getAppsFromRegistry("HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall"));
        apps.addAll(getAppsFromRegistry("HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall"));
        
        // 补充从常见安装目录扫描
        apps.addAll(scanInstallDirectories());
        
        return apps;
    }
    
    /**
     * 从注册表获取应用程序信息
     */
    private static List<AppInfo> getAppsFromRegistry(String registryPath) {
        List<AppInfo> apps = new ArrayList<>();
        
        try {
            // 执行reg命令查询注册表
            Process process = Runtime.getRuntime().exec(
                new String[]{"cmd", "/c", "reg", "query", registryPath}
            );
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "GBK")
            );
            
            String line;
            List<String> subKeys = new ArrayList<>();
            
            // 读取所有子键
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("HKEY")) {
                    subKeys.add(line.trim());
                }
            }
            
            reader.close();
            process.waitFor();
            
            // 查询每个子键的详细信息
            for (String subKey : subKeys) {
                AppInfo app = getAppInfoFromRegistryKey(subKey);
                if (app != null && app.getName() != null && !app.getName().isEmpty()) {
                    apps.add(app);
                }
            }
            
        } catch (Exception e) {
            System.err.println("读取注册表失败 (" + registryPath + "): " + e.getMessage());
        }
        
        return apps;
    }
    
    /**
     * 从注册表键获取应用程序详细信息
     */
    private static AppInfo getAppInfoFromRegistryKey(String registryKey) {
        try {
            Map<String, String> regValues = new HashMap<>();
            
            // 查询该键的所有值
            Process process = Runtime.getRuntime().exec(
                new String[]{"cmd", "/c", "reg", "query", "\"" + registryKey + "\""}
            );
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "GBK")
            );
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("REG_SZ") || line.contains("REG_DWORD") || 
                    line.contains("REG_EXPAND_SZ") || line.contains("REG_MULTI_SZ")) {
                    
                    String[] parts = line.split("\\s{4,}");
                    if (parts.length >= 3) {
                        String key = parts[1].trim();
                        String value = parts[2].trim();
                        regValues.put(key, value);
                        
                        // 合并多行值
                        for (int i = 3; i < parts.length; i++) {
                            value += " " + parts[i].trim();
                        }
                        regValues.put(key, value);
                    }
                }
            }
            
            reader.close();
            process.waitFor();
            
            // 检查是否为系统组件或不需要的应用
            String systemComponent = regValues.get("SystemComponent");
            String parentKeyName = regValues.get("ParentKeyName");
            String releaseType = regValues.get("ReleaseType");
            
            if ("1".equals(systemComponent) || parentKeyName != null || 
                "Security Update".equals(releaseType) || "Update".equals(releaseType)) {
                return null;
            }
            
            // 获取应用名称（优先使用DisplayName）
            String name = regValues.get("DisplayName");
            if (name == null || name.trim().isEmpty()) {
                return null; // 跳过没有名称的应用
            }
            
            AppInfo app = new AppInfo();
            app.setName(name);
            app.setVersion(regValues.get("DisplayVersion"));
            app.setInstallPath(regValues.get("InstallLocation"));
            
            // 处理安装时间
            String installDate = regValues.get("InstallDate");
            if (installDate != null && !installDate.isEmpty()) {
                try {
                    // 尝试解析YYYYMMDD格式
                    if (installDate.length() == 8) {
                        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMdd");
                        Date date = inputFormat.parse(installDate);
                        app.setInstallTime(DATE_FORMAT.format(date));
                    }
                } catch (Exception e) {
                    // 如果解析失败，保持原样
                    app.setInstallTime(installDate);
                }
            }
            
            // 尝试从安装路径获取文件时间信息
            if (app.getInstallPath() != null && !app.getInstallPath().isEmpty()) {
                enhanceAppInfoWithFileAttributes(app);
            }
            
            return app;
            
        } catch (Exception e) {
            // 忽略无法读取的应用
            return null;
        }
    }
    
    /**
     * 扫描常见安装目录
     */
    private static List<AppInfo> scanInstallDirectories() {
        List<AppInfo> apps = new ArrayList<>();
        List<String> commonDirs = Arrays.asList(
            "C:\\Program Files",
            "C:\\Program Files (x86)",
            "D:\\Program Files",
            "D:\\Program Files (x86)"
        );
        
        for (String dir : commonDirs) {
            File programDir = new File(dir);
            if (programDir.exists() && programDir.isDirectory()) {
                scanDirectory(programDir, apps);
            }
        }
        
        return apps;
    }
    
    /**
     * 递归扫描目录
     */
    private static void scanDirectory(File dir, List<AppInfo> apps) {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                // 检查是否为应用程序目录
                if (isLikelyAppDirectory(file)) {
                    AppInfo app = createAppInfoFromDirectory(file);
                    if (app != null) {
                        apps.add(app);
                    }
                }
            }
        }
    }
    
    /**
     * 判断是否可能是应用程序目录
     */
    private static boolean isLikelyAppDirectory(File dir) {
        String name = dir.getName().toLowerCase();
        
        // 排除明显不是应用目录的文件夹
        if (name.contains("windows") || name.contains("microsoft") || 
            name.contains("common files") || name.contains("internet explorer")) {
            return false;
        }
        
        // 检查目录中是否有可执行文件
        File[] files = dir.listFiles((d, fileName) -> 
            fileName.toLowerCase().endsWith(".exe") && !fileName.toLowerCase().contains("uninstall")
        );
        
        return files != null && files.length > 0;
    }
    
    /**
     * 从目录创建应用信息
     */
    private static AppInfo createAppInfoFromDirectory(File dir) {
        AppInfo app = new AppInfo();
        app.setName(dir.getName());
        app.setInstallPath(dir.getAbsolutePath());
        
        // 尝试查找主要的exe文件
        File[] exeFiles = dir.listFiles((d, fileName) -> 
            fileName.toLowerCase().endsWith(".exe") && !fileName.toLowerCase().contains("uninstall")
        );
        
        if (exeFiles != null && exeFiles.length > 0) {
            // 使用第一个exe文件的时间作为参考
            enhanceAppInfoWithFileAttributes(app);
        }
        
        return app;
    }
    
    /**
     * 通过文件属性增强应用信息
     */
    private static void enhanceAppInfoWithFileAttributes(AppInfo app) {
        try {
            Path installPath = Paths.get(app.getInstallPath());
            if (Files.exists(installPath)) {
                BasicFileAttributes attrs = Files.readAttributes(installPath, BasicFileAttributes.class);
                
                // 设置安装时间（创建时间）
                if (app.getInstallTime() == null) {
                    FileTime creationTime = attrs.creationTime();
                    app.setInstallTime(DATE_FORMAT.format(new Date(creationTime.toMillis())));
                }
                
                // 设置更新时间（最后修改时间）
                FileTime modifiedTime = attrs.lastModifiedTime();
                app.setUpdateTime(DATE_FORMAT.format(new Date(modifiedTime.toMillis())));
            }
            
            // 尝试获取版本信息
            if (app.getVersion() == null) {
                // 在安装目录中查找exe文件并尝试获取版本
                File dir = new File(app.getInstallPath());
                if (dir.exists() && dir.isDirectory()) {
                    File[] exeFiles = dir.listFiles((d, name) -> 
                        name.toLowerCase().endsWith(".exe") && !name.toLowerCase().contains("uninstall")
                    );
                    
                    if (exeFiles != null && exeFiles.length > 0) {
                        String version = getExeVersion(exeFiles[0].getAbsolutePath());
                        if (version != null) {
                            app.setVersion(version);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            // 忽略文件属性读取错误
        }
    }
    
    /**
     * 获取exe文件的版本信息
     */
    private static String getExeVersion(String exePath) {
        try {
            // 使用Windows命令获取文件版本
            Process process = Runtime.getRuntime().exec(
                new String[]{"cmd", "/c", "wmic", "datafile", "where", "name=\"" + exePath + "\"", "get", "version"}
            );
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.equalsIgnoreCase("Version")) {
                    return line;
                }
            }
            
            reader.close();
            process.waitFor();
        } catch (Exception e) {
            // 忽略版本获取错误
        }
        return null;
    }
    
    /**
     * 过滤应用程序（排除UWP和系统组件）
     */
    private static List<AppInfo> filterApps(List<AppInfo> apps) {
        List<AppInfo> filtered = new ArrayList<>();
        Set<String> uniqueNames = new HashSet<>();
        
        for (AppInfo app : apps) {
            // 跳过空名称的应用
            if (app.getName() == null || app.getName().trim().isEmpty()) {
                continue;
            }
            
            // 跳过重复的应用
            String normalizedName = app.getName().toLowerCase().trim();
            if (uniqueNames.contains(normalizedName)) {
                continue;
            }
            
            // 检查是否为UWP应用
            if (isUwpApp(app)) {
                continue;
            }
            
            // 检查是否需要排除的系统组件
            if (isExcludedApp(app)) {
                continue;
            }
            
            uniqueNames.add(normalizedName);
            filtered.add(app);
        }
        
        // 按名称排序
        filtered.sort(Comparator.comparing(AppInfo::getName, String.CASE_INSENSITIVE_ORDER));
        
        return filtered;
    }
    
    /**
     * 判断是否为UWP应用
     */
    private static boolean isUwpApp(AppInfo app) {
        String name = app.getName().toLowerCase();
        String path = app.getInstallPath() != null ? app.getInstallPath().toLowerCase() : "";
        
        // 检查UWP特征
        for (String indicator : UWP_INDICATORS) {
            if (path.contains(indicator.toLowerCase())) {
                return true;
            }
        }
        
        // 检查是否为Microsoft Store应用
        if (name.contains("microsoft") && (name.contains("store") || name.contains("xbox"))) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 判断是否是需要排除的应用
     */
    private static boolean isExcludedApp(AppInfo app) {
        String name = app.getName();
        
        for (Pattern pattern : EXCLUDE_PATTERNS) {
            Matcher matcher = pattern.matcher(name);
            if (matcher.matches()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 转换为JSON格式
     */
    private static String convertToJson(List<AppInfo> apps) {
        JSONArray jsonArray = new JSONArray();
        
        for (AppInfo app : apps) {
            JSONObject jsonApp = new JSONObject();
            jsonApp.put("name", app.getName());
            jsonApp.put("version", app.getVersion() != null ? app.getVersion() : JSONObject.NULL);
            jsonApp.put("install_time", app.getInstallTime() != null ? app.getInstallTime() : JSONObject.NULL);
            jsonApp.put("update_time", app.getUpdateTime() != null ? app.getUpdateTime() : JSONObject.NULL);
            jsonApp.put("install_path", app.getInstallPath() != null ? app.getInstallPath() : JSONObject.NULL);
            jsonArray.put(jsonApp);
        }
        
        JSONObject result = new JSONObject();
        result.put("total_apps", apps.size());
        result.put("collection_time", DATE_FORMAT.format(new Date()));
        result.put("apps", jsonArray);
        
        return result.toString(4); // 缩进4个空格，美化输出
    }
    
    /**
     * 保存到文件
     */
    private static void saveToFile(String jsonContent) {
        try (FileWriter writer = new FileWriter(OUTPUT_FILE)) {
            writer.write(jsonContent);
        } catch (IOException e) {
            System.err.println("保存文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 应用程序信息类
     */
    static class AppInfo {
        private String name;
        private String version;
        private String installTime;
        private String updateTime;
        private String installPath;
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public String getInstallTime() { return installTime; }
        public void setInstallTime(String installTime) { this.installTime = installTime; }
        
        public String getUpdateTime() { return updateTime; }
        public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
        
        public String getInstallPath() { return installPath; }
        public void setInstallPath(String installPath) { this.installPath = installPath; }
    }
}
