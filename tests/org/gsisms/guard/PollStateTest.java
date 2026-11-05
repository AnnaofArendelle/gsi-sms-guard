package org.gsisms.guard;
public final class PollStateTest {
    private static int checks;
    private static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    public static void main(String[] args){
        String good="none\n0\n22:300\n11:400\n";
        PollState p=PollState.parse(good);check(!p.builtin&&!p.mounted,"ordinary environment hints");
        check(p.identity.equals("[11:400, 22:300]"),"stable process ordering");
        check(PollState.parse(good.replace("none","builtin")).builtin,"built-in repair reported as hint");
        check(PollState.parse(good.replace("\n0\n","\n1\n")).mounted,"mount reported as hint");
        check(PollState.parse("none\n0\n").identity.isEmpty(),"unknown process names allow periodic fallback");
        check(!PollState.parse(good.replace("22:300","22:301")).identity.equals(p.identity),"new start time detected");
        check(PollState.parse(good.replace("\n","\r\n")).identity.equals(p.identity),"CRLF root transport");
        check(PollState.parse(good+"garbage\n").identity.equals(p.identity),"invalid process output ignored");
        for(String invalid:new String[]{"short","none\nx\n","unknown\n0\n"}){
            boolean failed=false;try{PollState.parse(invalid);}catch(IllegalArgumentException ex){failed=true;}check(failed,"malformed hints rejected");
        }
        Compatibility generic=new Compatibility(true,false,false,false,false,true,false,false);
        check(generic.readyRecommended(),"ATEL needs neither database, master mount namespace nor named services");
        Compatibility logged=new Compatibility(true,false,false,false,false,false,true,false);
        check(logged.readyRecommended(),"buffer evidence supports ATEL without a database");
        Compatibility missing=new Compatibility(false,false,false,false,false,false,false,false);
        check(!missing.allowReady(false)&&missing.allowReady(true),"warning override reaches actual ATEL attempt");
        check(missing.readyWarnings().contains("OEM hook")&&missing.readyWarnings().contains("Power Opt"),"warnings explain missing conditions");
        Compatibility builtin=new Compatibility(true,true,false,true,true,true,false,true);
        check(!builtin.allowReady(false)&&builtin.allowReady(true),"built-in warning is overridable");
        Compatibility mounted=new Compatibility(true,false,true,true,true,true,false,true);
        check(!mounted.allowReady(false)&&mounted.allowReady(true),"mount warning is overridable for ATEL");
        Compatibility mount=new Compatibility(false,false,false,true,true,true,false,true);
        check(mount.mountRecommended(),"mount policy checks its own prerequisites, not OEM hook or device");
        check(!missing.allowMount(false)&&missing.allowMount(true),"warning override reaches mount preflight");
        check(missing.mountWarnings().contains("数据库")&&missing.mountWarnings().contains("挂载空间"),"mount prerequisite failures explained");
        System.out.println(checks+" compatibility, warning-override and polling checks passed");
    }
}
