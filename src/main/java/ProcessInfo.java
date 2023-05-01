public class ProcessInfo implements Comparable<ProcessInfo> {
    public ProcessInfo(String name, String type, String exe, String version) {
        this.name = name;
        this.type = type;
        this.exe = exe;
        this.version = version;
    }

    public String name;
    public String type;
    public String exe;
    public String version;

    @Override
    public int compareTo(ProcessInfo o) {
        return o.name.compareTo(name);
    }
}