import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SteamReader {
    public static TreeSet<GameInfo> getSteamGames(String dbPath) {
        ArrayList<SteamReader.AppInfo> apps;
        TreeSet<GameInfo> games = new TreeSet<>();
        try {
            apps = GetSteamApps(GetSteamLibs());
        } catch (Exception ex) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            ex.printStackTrace();
            return games;
        }
        //получаем пути сейвов из библиотеки
        TreeSet<GameInfo> libMappings = new TreeSet<>();
        try {
            DBWorker DBW = new DBWorker(dbPath.replace("UserGame", "GameMapper"));
            libMappings = DBW.readDBSteam();
            DBW.closeDB();
        } catch (ClassNotFoundException | SQLException err) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            err.printStackTrace();
        }

        for (AppInfo appInfo : apps) {
            String sPathSteam = "";
            for (GameInfo libMapping : libMappings) {
                System.out.printf("%1$tF %1$tT %2$s %3$s %4$s", new Date(), ":: Найдена папка сейвов для AppId ", libMapping.gName, " : " + libMapping.sPath + "\n" );
                if (Objects.equals(appInfo.AppId, libMapping.gName)) {
                    System.out.printf("%1$tF %1$tT %2$s %3$s %4$s", new Date(), ":: Папка сейвов прописана для AppId ", appInfo.Name, " : " + libMapping.sPath + "\n" );
                    sPathSteam = libMapping.sPath;
                    break;
                }
            }
            GameInfo gi = new GameInfo(
                    appInfo.Name,
                    appInfo.Executable,
                    "STEAM",
                    appInfo.BuildId,
                    sPathSteam
            );
            games.add(gi);
        }

        return games;
    }

    private static ArrayList<AppInfo> GetSteamApps(ArrayList<String> steamLibs) {
        var apps = new ArrayList<AppInfo>();
        for (var lib : steamLibs) {
            var appMetaDataPath = Paths.get(lib).resolve("SteamApps").toString();
            var files = Directory.GetFiles(appMetaDataPath, "*.acf");
            for (var file : files) {
                try {
                    var appInfo = GetAppInfo(file);
                    if (appInfo != null) {
                        apps.add(appInfo);
                    }
                } catch (Exception ex) {
                    System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
                    ex.printStackTrace();
                }
            }
        }
        return apps;
    }

    private static AppInfo GetAppInfo(String appMetaFile) throws Exception {
        var fileDataLines = Files.readAllLines(java.nio.file.Path.of(appMetaFile));

        var dic = new HashMap<String, String>();

        for (var line : fileDataLines) {
            Pattern pattern = Pattern.compile("\\s*\"(?<key>\\w+)\"\\s+\"(?<val>.*)\"");
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                var key = matcher.group("key");
                var val = matcher.group("val");
                dic.put(key, val);
            }
        }

        AppInfo appInfo = null;

        if (dic.keySet().size() > 0) {
            appInfo = new AppInfo();
            appInfo.Name = dic.get("name");
            appInfo.BuildId = dic.get("buildid");
            appInfo.AppId = dic.get("appid");

            appInfo.GameRoot = Path.combine((new File(appMetaFile)).getParent(), "common", dic.get("installdir"));
            if (!(new File(appInfo.GameRoot)).isDirectory()) {
                return null;
            }

            appInfo.Executable = GetExecutable(appInfo);
        }

        return appInfo;
    }


    private static String _appInfoText = null;

    private static String GetExecutable(AppInfo appInfo) throws Exception {
        if (_appInfoText == null) {
            var appInfoFile = Path.combine(GetSteamPath(), "appcache", "appinfo.vdf");
            var bytes = Files.readAllBytes(java.nio.file.Path.of(appInfoFile));
            _appInfoText = new String(bytes, StandardCharsets.UTF_8);
        }
        var startIndex = 0;
        int maxTries = 50;
        var fullName = "";

        do {
            var startOfDataArea = _appInfoText.indexOf(String.format("\u0000\u0001name\u0000%1$s\u0000", appInfo.Name), startIndex);
            if (startOfDataArea < 0 && maxTries == 50) {
                startOfDataArea = _appInfoText.indexOf(String.format("\u0000\u0001gamedir\u0000%1$s\u0000", appInfo.Name), startIndex); //Alternative1
            }
            if (startOfDataArea < 0 && maxTries == 50) {
                startOfDataArea = _appInfoText.indexOf(String.format("\u0000\u0001name\u0000%1$s\u0000", appInfo.Name), startIndex); //Alternative2
            }
            if (startOfDataArea > 0) {
                startIndex = startOfDataArea + 13;
                int nextLaunch = -1;
                do {
                    var executable = _appInfoText.indexOf("\u0000\u0001executable\u0000", startOfDataArea);
                    if (executable > -1 && nextLaunch == -1) {
                        nextLaunch = _appInfoText.indexOf("\u0000\u0001launch\u0000", executable);
                    }

                    if ((nextLaunch <= 0 || executable < nextLaunch) && executable > 0) {
                        executable += 13;
                        StringBuilder filename = new StringBuilder();
                        while (_appInfoText.charAt(executable) != '\u0000') {
                            filename.append(_appInfoText.charAt(executable));
                            executable++;
                        }
                        if (filename.toString().contains("://")) {
                            //EA or other external
                            return filename.toString(); //Need to use other means to grab the EXE here.
                        }

                        fullName = Paths.get(appInfo.GameRoot).resolve(filename.toString()).toString();

                        startOfDataArea = executable + 1;
                        startIndex = startOfDataArea + 10;
                    } else {
                        break;
                    }
                } while (!(new File(fullName)).isFile() && maxTries-- > 0);
            } else {
                return null;
            }
        } while (!(new File(fullName)).isFile() && maxTries-- > 0);

        if ((new File(fullName)).isFile()) {
            return fullName;
        }

        return null;
    }

    private static ArrayList<String> GetSteamLibs() throws Exception {

        var steamPath = GetSteamPath();
        var libraries = new ArrayList<>(List.of(steamPath));

        var listFile = Paths.get(steamPath).resolve("steamapps\\libraryfolders.vdf").toString();
        var lines = Files.readAllLines(java.nio.file.Path.of(listFile));
        for (var line : lines) {
            Pattern pattern = Pattern.compile("\"(?<path>\\w:\\\\\\\\.*)\"");
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                var path = matcher.group("path").replace("\\\\", "\\");
                if ((new File(path)).isDirectory()) {
                    libraries.add(path);
                }
            }
        }
        Set<String> set = new LinkedHashSet<>(libraries);
        libraries = new ArrayList<>(set);
        return libraries;
    }

    public static String getRegValue(String keyPath, String keyName)
            throws IOException, InterruptedException {
        Process keyReader = Runtime.getRuntime().exec("reg query " + keyPath + " /v " + keyName);

        BufferedReader outputReader;
        String readLine;
        StringBuilder stringBuilder = new StringBuilder();

        outputReader = new BufferedReader(new InputStreamReader(keyReader.getInputStream()));

        while ((readLine = outputReader.readLine()) != null) {
            stringBuilder.append(readLine);
        }

        String[] outputComponents = stringBuilder.toString().split(" {4}");

        keyReader.waitFor();

        return outputComponents[outputComponents.length - 1];
    }

    private static String GetSteamPath() throws Exception {
        String steam32path = getRegValue("HKLM\\SOFTWARE\\VALVE\\Steam", "InstallPath");
        if (steam32path != null && !steam32path.equals("")) return steam32path;

        String steam64path = getRegValue("HKLM\\SOFTWARE\\Wow6432Node\\Valve\\Steam", "InstallPath");
        if (steam64path != null && !steam64path.equals("")) return steam64path;

        throw new Exception("Steam не установлен");
    }

    private static class AppInfo {
        public String Name;
        public String GameRoot;
        public String Executable;
        public String BuildId;
        public String AppId;
    }

    private static class Directory {
        public static String[] GetFiles(String path, final String searchPattern) {
            // Split the searchPattern incase we have directories in there
            ArrayList<String> pattern = new ArrayList<>(Arrays.asList(searchPattern.split("/")));
            // Take the last element out from the array, as this will be the file pattern
            String filePattern = pattern.remove(pattern.size() - 1);
            // Insert the base path into the remaining list
            pattern.add(0, path);
            // Now lets concat the lot to create a base path
            path = Path.combine(pattern.toArray(new String[0]));

            final Pattern regEx = Pattern.compile(filePattern.replace("*", ".*").replace("?", ".?"));
            File directory = new File(path);

            File[] matchingFiles = directory.listFiles((dir, filename) -> new File(dir, filename).isFile() && regEx.matcher(filename).matches());

            ArrayList<String> files = new ArrayList<>();
            assert matchingFiles != null;
            for (File file : matchingFiles) {
                files.add(file.getAbsolutePath());
            }

            return files.toArray(new String[0]);
        }
    }

    private static class Path {
        public static String combine(String... paths) {
            File file = new File(paths[0]);

            for (int i = 1; i < paths.length; i++) {
                if (paths[i] != null)
                    file = new File(file, paths[i]);
            }

            return file.getPath();
        }
    }
}
