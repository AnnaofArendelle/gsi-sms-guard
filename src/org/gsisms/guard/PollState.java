package org.gsisms.guard;
import java.util.TreeSet;
/** Pure parser so device guards can be tested without Android or root. */
final class PollState {
    boolean gsi,eligible,mounted;
    String identity="";
    static PollState parse(String output){
        String[] lines=output.split("\\r?\\n");
        if(lines.length<6)throw new IllegalArgumentException("无法读取无线服务状态");
        PollState r=new PollState();r.gsi=lines[0].trim().equals("1");
        r.eligible=r.gsi&&lines[1].trim().equals("cupid")&&lines[2].trim().equals("none")&&lines[3].trim().equals(lines[4].trim());
        r.mounted=lines[5].trim().equals("1");
        TreeSet<String> ids=new TreeSet<>();
        for(int i=6;i<lines.length;i++)if(lines[i].trim().matches("[0-9]+:[0-9]+"))ids.add(lines[i].trim());
        if(!ids.isEmpty())r.identity=ids.toString();return r;
    }
}
