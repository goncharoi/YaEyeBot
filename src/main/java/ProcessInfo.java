public class ProcessInfo implements Comparable<ProcessInfo> {
    public ProcessInfo(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String name;
    public String type;

    @Override
    public int compareTo(ProcessInfo o) {
        return o.name.compareTo(name);
    }
}