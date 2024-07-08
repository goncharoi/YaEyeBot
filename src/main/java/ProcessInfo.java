public class ProcessInfo implements Comparable<ProcessInfo> {
    public ProcessInfo(String name, String type, String exe, String version, String handle) {
        this.name = name;
        this.type = type;
        this.exe = exe;
        this.version = version;
        this.handle = handle;
    }

    public String name;
    public String type;
    public String exe;
    public String version;
    public String handle;

    @Override
    public int compareTo(ProcessInfo o) {
        return o.handle.compareTo(handle);
    }
}