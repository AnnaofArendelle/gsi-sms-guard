package org.gsisms.guard;
import java.util.TreeSet;
/** Runtime hints only. No model, ROM, DSU or mount-namespace allowlist for ATEL. */
final class PollState {
    boolean builtin,mounted;
    String identity="";
    static PollState parse(String output){
        String[] lines=output.split("\\r?\\n",-1);
        if(lines.length<2||(!lines[0].equals("builtin")&&!lines[0].equals("none"))
                ||(!lines[1].equals("0")&&!lines[1].equals("1")))
            throw new IllegalArgumentException("无法读取无线服务状态");
        PollState r=new PollState();r.builtin=lines[0].equals("builtin");r.mounted=lines[1].equals("1");
        TreeSet<String> ids=new TreeSet<>();
        for(int i=2;i<lines.length;i++)if(lines[i].trim().matches("[0-9]+:[0-9]+"))ids.add(lines[i].trim());
        if(!ids.isEmpty())r.identity=ids.toString();return r;
    }
}
