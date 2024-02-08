public class ProcessInfo implements Comparable<ProcessInfo> {
    public ProcessInfo(String name, String type, String exe, String version, String pid) {
        this.name = name;
        this.type = type;
        this.exe = exe;
        this.version = version;
//        this.window = false;
        this.pid = pid;
    }

    public String name;
    public String type;
    public String exe;
    public String version;
//    public Boolean window;
    public String pid;

    @Override
    public int compareTo(ProcessInfo o) {
        return o.pid.compareTo(pid);
    }
}