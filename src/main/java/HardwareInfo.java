public class HardwareInfo implements Comparable<HardwareInfo> {
    public HardwareInfo(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String name;
    public String value;

    @Override
    public int compareTo(HardwareInfo o) {
        return o.name.compareTo(name);
    }
}