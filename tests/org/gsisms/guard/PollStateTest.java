package org.gsisms.guard;
public final class PollStateTest {
    private static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
    public static void main(String[] args){
        String good="1\ncupid\nnone\nmnt:[1]\nmnt:[1]\n0\n22:300\n11:400\n";
        PollState p=PollState.parse(good);check(p.gsi&&p.eligible&&!p.mounted,"supported environment");
        check(p.identity.equals("[11:400, 22:300]"),"stable process ordering");
        check(!PollState.parse(good.replaceFirst("1\\n","\\n")).eligible,"stock OS blocked");
        check(!PollState.parse(good.replace("cupid","other")).eligible,"unverified device blocked");
        check(!PollState.parse(good.replace("none","builtin")).eligible,"built-in fix blocked");
        check(!PollState.parse(good.replaceFirst("mnt:\\[1\\]","mnt:[2]")).eligible,"private namespace blocked");
        check(PollState.parse(good.replace("\n0\n","\n1\n")).mounted,"mount suspends auto ATEL");
        check(PollState.parse(good.replace("22:300\n11:400\n","")).identity.isEmpty(),"no RIL waits");
        check(!PollState.parse(good.replace("22:300","22:301")).identity.equals(p.identity),"same PID with new start time reasserts");
        check(PollState.parse(good.replace("\n","\r\n")).eligible,"CRLF transport");
        boolean invalid=false;try{PollState.parse("short");}catch(IllegalArgumentException e){invalid=true;}check(invalid,"incomplete output blocked");
        System.out.println("11 device guard/polling checks passed");
    }
}
