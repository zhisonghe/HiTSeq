/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hitseq.annotation;

import java.util.*;

/**
 *
 * @author hezhisong
 */
public class ASEventSet {
    private HashSet<ASEvent> events;
    private ArrayList<ArrayList<ASEvent>> eventsInType;
    private ArrayList<HashMap<String,ArrayList<ASEvent>>> eventsInTypeInGroup;
    
    public ASEventSet(JunctionSet junctions){
        if(junctions.getJunctionGroups()==null)
            junctions.groupJuncSet();
        HashMap<String, HashSet<Junction>> juncGroups=junctions.getJunctionGroups();
        
        events=new HashSet<>();
        eventsInType=new ArrayList<>();
        eventsInTypeInGroup=new ArrayList<>();
        
        for(String group : juncGroups.keySet()){
            // if there is only one junction in the group, skip it
            if(juncGroups.get(group).size()<2)
                continue;
            
            if(eventsInTypeInGroup.isEmpty())
                for(int i=0;i<4;i++)
                    eventsInTypeInGroup.add(new HashMap<String,ArrayList<ASEvent>>());
            
            // sort junctions in the group
            TreeSet<Junction> sortedJunctionsGroupTreeSet=new TreeSet<>(new Comparator<Junction>(){
                @Override
                public int compare(Junction arg0, Junction arg1) {
                    return(arg0.compareTo(arg1));
                }
            });
            sortedJunctionsGroupTreeSet.addAll(juncGroups.get(group));
            ArrayList<Junction> sortedJunctionsGroup=new ArrayList<>(); // the sorted list of junctions in this group
            ArrayList<Integer> sortedJunctionCoord=new ArrayList<>(); // the sorted sites of junction site (exon boundary); the overlapping sites will be collapsed
            for(Iterator<Junction> it=sortedJunctionsGroupTreeSet.iterator(); it.hasNext();){
                Junction junc=it.next();
                sortedJunctionsGroup.add(junc);
                
                if(sortedJunctionCoord.isEmpty()){
                    sortedJunctionCoord.add(junc.getStartSite());
                    sortedJunctionCoord.add(junc.getEndSite());
                } else{
                    int i=sortedJunctionCoord.size()-1;
                    while(i>0 && sortedJunctionCoord.get(i)>junc.getEndSite())
                        i--;
                    if(!sortedJunctionCoord.get(i).equals(junc.getEndSite()))
                        sortedJunctionCoord.add(i+1, junc.getEndSite());
                    while(i>0 && sortedJunctionCoord.get(i)>junc.getStartSite())
                        i--;
                    if(!sortedJunctionCoord.get(i).equals(junc.getStartSite()))
                        sortedJunctionCoord.add(i+1, junc.getStartSite());
                }
            }
            
            // if there is no touched junction in the group, skip it
            if(sortedJunctionCoord.size()==sortedJunctionsGroup.size()*2) // if there is touched junction, because of the collapse of junction sites, the number of coordinates should be smaller than 2 folds of the number of junctions
                continue;
            
            ArrayList<ArrayList<ASEvent>> eventsInTypeThisGroup=new ArrayList<>();
            
            // Look for skipped exon events
            eventsInType.add(new ArrayList<ASEvent>());
            eventsInTypeThisGroup.add(new ArrayList<ASEvent>());
            if(sortedJunctionsGroup.size()>=3){
                for(int i=1; i<sortedJunctionsGroup.size()-1; i++){
                    /*
                     * assume this junction is the exclusive junction of a skipped exon event,
                     * try to identify its corresponding inclusive junction pairs.
                     * if succeed, add a skipped exon event
                     */
                    Junction exclu=sortedJunctionsGroup.get(i); 
                    ArrayList<Junction> excluList=new ArrayList<>();
                    excluList.add(exclu);
                    ArrayList<Junction> incluLeft=new ArrayList<>();
                    ArrayList<Junction> incluRight=new ArrayList<>();

                    for(int j=i-1; j>=0; j--){
                        Junction left=sortedJunctionsGroup.get(j);
                        if(exclu.withStartSite(left.getStartSite()))
                            incluLeft.add(left);
                    }
                    if(incluLeft.isEmpty()) continue;
                    for(int j=i+1; j<sortedJunctionsGroup.size(); j++){
                        Junction right=sortedJunctionsGroup.get(j);
                        if(exclu.withEndSite(right.getEndSite()))
                            incluRight.add(right);
                    }
                    if(incluRight.isEmpty()) continue;
                    
                    // if there is any inclusive junction which has no compatible paired junction, remove it
                    ArrayList<Junction> unfilteredIncluLeft=new ArrayList<>();
                    unfilteredIncluLeft.addAll(incluLeft);
                    for(Junction juncLeft : unfilteredIncluLeft){
                        boolean compatible=false;
                        for(Junction juncRight : incluRight)
                            if(!juncLeft.cross(juncRight)){
                                compatible=true;
                                break;
                            }
                        if(!compatible) incluLeft.remove(juncLeft);
                    }
                    if(incluLeft.isEmpty()) continue;
                    ArrayList<Junction> unfilteredIncluRight=new ArrayList<>();
                    unfilteredIncluRight.addAll(incluRight);
                    for(Junction juncRight : unfilteredIncluRight){
                        boolean compatible=false;
                        for(Junction juncLeft : incluLeft)
                            if(!juncLeft.cross(juncRight)){
                                compatible=true;
                                break;
                            }
                        if(!compatible) incluRight.remove(juncRight);
                    }
                    if(incluRight.isEmpty()) continue;
                    
                    // output
                    ASEvent event=new ASEvent(ASEvent.SKIPP_EXON, incluLeft, incluRight, excluList);
                    events.add(event);
                    eventsInType.get(ASEvent.SKIPP_EXON).add(event);
                    eventsInTypeThisGroup.get(ASEvent.SKIPP_EXON).add(event);
                }
            }
            //System.err.println("generated skipped exon for group "+group);
            
            // Look for mutual exclusive exons event
            eventsInType.add(new ArrayList<ASEvent>());
            eventsInTypeThisGroup.add(new ArrayList<ASEvent>());
            ArrayList<ArrayList<Junction>> mxeEventsBeforeMerge=new ArrayList<>(); // MXE event list before merging
            if(sortedJunctionsGroup.size()>=4){
                ArrayList<ArrayList<Junction>> eventCandidates=new ArrayList<>(); // event candidates and their states
                
                for(int i=1; i<sortedJunctionsGroup.size(); i++){
                    Junction thisJunc=sortedJunctionsGroup.get(i);
                    
                    if(!eventCandidates.isEmpty()){
                        ArrayList<ArrayList<Junction>> existedCandidates=new ArrayList<>();
                        existedCandidates.addAll(eventCandidates);

                        //System.err.println(eventCandidates.size());
                        for(ArrayList<Junction> candidate : existedCandidates){
                            if(candidate.size()==2){
                                if(thisJunc.cross(candidate.get(1)) && !thisJunc.cross(candidate.get(0))){
                                    ArrayList<Junction> newCandidate=new ArrayList<>();
                                    newCandidate.addAll(candidate);
                                    newCandidate.add(thisJunc);
                                    eventCandidates.add(newCandidate);
                                }
                            } else if(candidate.size()==3)
                                if(thisJunc.withEndSite(candidate.get(2).getEndSite()) && !thisJunc.cross(candidate.get(1))){
                                    ArrayList<Junction> eventJuncs=new ArrayList<>();
                                    eventJuncs.addAll(candidate);
                                    eventJuncs.add(thisJunc);
                                    mxeEventsBeforeMerge.add(eventJuncs);
                                }
                        }
                    }

                    // firstly, if there are two touched junctions, add them as the initial candidate
                    int j=i-1;
                    while(j>=0 && sortedJunctionsGroup.get(j).getStartSite()==thisJunc.getStartSite()){ 
                        ArrayList<Junction> candidate=new ArrayList<>();
                        candidate.add(sortedJunctionsGroup.get(j));
                        candidate.add(thisJunc);
                        eventCandidates.add(candidate);
                        j--;
                    }
                }
            }
            //System.err.println("generated mutual exclusive exon for group "+group);
            
            // Look for alternative 5'/3' end
            eventsInType.add(new ArrayList<ASEvent>());
            eventsInTypeThisGroup.add(new ArrayList<ASEvent>());
            if(juncGroups.get(group).size()>1 && sortedJunctionsGroup.size()*2!=sortedJunctionCoord.size()){
                // first check start-shared junctions
                int i=0;
                ArrayList<Junction> juncShareStart=new ArrayList<>();
                while(i<sortedJunctionsGroup.size()){
                    if(juncShareStart.isEmpty()){
                        juncShareStart.add(sortedJunctionsGroup.get(i));
                        i++;
                        continue;
                    }
                    else if(juncShareStart.get(0).withStartSite(sortedJunctionsGroup.get(i).getStartSite())){
                        juncShareStart.add(sortedJunctionsGroup.get(i));
                        i++;
                        if(i!=sortedJunctionsGroup.size())
                            continue;
                        else
                            i--;
                    }
                    
                    if(juncShareStart.size()>1)
                        for(int j1=0; j1<juncShareStart.size()-1; j1++)
                            for(int j2=j1+1; j2<juncShareStart.size(); j2++){
                                ArrayList<Junction> candidates=new ArrayList<>();
                                candidates.add(juncShareStart.get(j1));candidates.add(juncShareStart.get(j2));

                                // check whether these two junctions are already involved in other SE/MXE(before merge) events
                                boolean involved=false;
                                for(ASEvent event : eventsInTypeThisGroup.get(ASEvent.SKIPP_EXON))
                                    if(event.containAll(candidates) && !event.getInclusiveJunctions().containsAll(candidates)){
                                        involved=true;
                                        break;
                                    }
                                if(!involved)
                                    for(ArrayList<Junction> mxeJuncs : mxeEventsBeforeMerge)
                                        if(mxeJuncs.containsAll(candidates)){
                                            involved=true;
                                            break;
                                        }

                                if(!involved){ // if they are not involved in other events, add them as an alternative 5'/3' end events
                                    ArrayList<Junction> newEventInclu=new ArrayList<>();
                                    ArrayList<Junction> newEventExclu=new ArrayList<>();
                                    newEventInclu.add(candidates.get(0));
                                    newEventExclu.add(candidates.get(1));
                                    ASEvent newEvent=new ASEvent(ASEvent.ALT_END, newEventInclu, newEventExclu);
                                    events.add(newEvent);
                                    eventsInType.get(ASEvent.ALT_END).add(newEvent);
                                    eventsInTypeThisGroup.get(ASEvent.ALT_END).add(newEvent);
                                }
                            }

                    juncShareStart.clear();
                    i++;
                }
                
                // second check end-shared junctions
                HashMap<Integer, ArrayList<Junction>> endOfJuncs=new HashMap<>();
                for(i=0; i<sortedJunctionsGroup.size(); i++){
                    if(!endOfJuncs.containsKey(sortedJunctionsGroup.get(i).getEndSite()))
                        endOfJuncs.put(sortedJunctionsGroup.get(i).getEndSite(), new ArrayList<Junction>());
                    endOfJuncs.get(sortedJunctionsGroup.get(i).getEndSite()).add(sortedJunctionsGroup.get(i));
                }
                for(ArrayList<Junction> juncsWithThisEnd : endOfJuncs.values()){
                    if(juncsWithThisEnd.size()>1){
                        for(i=0; i<juncsWithThisEnd.size()-1; i++){
                            for(int j=i+1; j<juncsWithThisEnd.size(); j++){
                                ArrayList<Junction> candidates=new ArrayList<>();
                                
                                candidates.add(juncsWithThisEnd.get(i)); candidates.add(juncsWithThisEnd.get(j));
                                boolean involved=false;
                                for(ASEvent event : eventsInTypeThisGroup.get(ASEvent.SKIPP_EXON))
                                    if(event.containAll(candidates) && !event.getInclusiveJunctions().containsAll(candidates)){
                                        involved=true;
                                        break;
                                    }
                                if(!involved)
                                    for(ArrayList<Junction> mxeJuncs : mxeEventsBeforeMerge)
                                        if(mxeJuncs.containsAll(candidates)){
                                            involved=true;
                                            break;
                                        }
                                
                                if(!involved){ // if they are not involved in other events, add them as an alternative 5'/3' end events
                                    ArrayList<Junction> newEventInclu=new ArrayList<>();
                                    ArrayList<Junction> newEventExclu=new ArrayList<>();
                                    newEventInclu.add(candidates.get(0));
                                    newEventExclu.add(candidates.get(1));
                                    ASEvent newEvent=new ASEvent(ASEvent.ALT_END, newEventInclu, newEventExclu);
                                    events.add(newEvent);
                                    eventsInType.get(ASEvent.ALT_END).add(newEvent);
                                    eventsInTypeThisGroup.get(ASEvent.ALT_END).add(newEvent);
                                }
                            }
                        }
                    }
                }
            }
            //System.err.println("generated alternative end for group "+group);
            
            // Merge MXE events
            // firstly determine the event pairs which can be merged together
            boolean[][] merged=new boolean[mxeEventsBeforeMerge.size()][mxeEventsBeforeMerge.size()];
            for(int i=0; i<mxeEventsBeforeMerge.size(); i++){
                ArrayList<Junction> juncsEvent1=mxeEventsBeforeMerge.get(i);
                for(int j=0; j<mxeEventsBeforeMerge.size(); j++){
                    if(i==j)
                        merged[i][j]=false;
                    else if(i>j)
                        merged[i][j]=merged[j][i];
                    else{
                        ArrayList<Junction> juncsEvent2=mxeEventsBeforeMerge.get(j);
                        int numJuncsCompatible=0;
                        for(int idx=0; idx<4; idx++){
                            if(juncsEvent1.get(idx).equals(juncsEvent2.get(idx)))
                                numJuncsCompatible++;
                            else if(juncsEvent1.get(idx).cross(juncsEvent2.get(idx)))
                                numJuncsCompatible++;
                            else if(juncsEvent1.get(idx).getStartSite()<juncsEvent2.get(idx).getStartSite() && juncsEvent2.get(idx).getEndSite()<juncsEvent1.get(idx).getEndSite())
                                numJuncsCompatible++;
                            else if(juncsEvent2.get(idx).getStartSite()<juncsEvent1.get(idx).getStartSite() && juncsEvent1.get(idx).getEndSite()<juncsEvent2.get(idx).getEndSite())
                                numJuncsCompatible++;
                            else{
                                ArrayList<Junction> incluCheck=new ArrayList<>();
                                ArrayList<Junction> excluCheck=new ArrayList<>();
                                if(juncsEvent1.get(idx).compareTo(juncsEvent2.get(idx))<0){
                                    incluCheck.add(juncsEvent1.get(idx));
                                    excluCheck.add(juncsEvent2.get(idx));
                                } else{
                                    incluCheck.add(juncsEvent2.get(idx));
                                    excluCheck.add(juncsEvent1.get(idx));
                                }
                                ASEvent check=new ASEvent(ASEvent.ALT_END, incluCheck, excluCheck);
                                if(eventsInTypeThisGroup.get(ASEvent.ALT_END).contains(check))
                                    numJuncsCompatible++;
                            }
                        }
                        
                        merged[i][j]=numJuncsCompatible==4;
                    }
                }
            }
            
            /*for(int i=0;i<merged.length;i++){
                String output="";
                for(int j=0;j<merged[i].length;j++)
                    output+=merged[i][j]+"\t";
                System.err.println(output);
            }*/
            
            // secondly, based on the boolean matrix, start merging
            ArrayList<Integer> remainingUnmergedMXEIdx=new ArrayList<>();
            ArrayList<Integer> toBeMergedIdx=new ArrayList<>();
            for(int i=0; i<mxeEventsBeforeMerge.size(); i++)
                remainingUnmergedMXEIdx.add(i);

            while(!remainingUnmergedMXEIdx.isEmpty()){
                if(toBeMergedIdx.isEmpty()){
                    toBeMergedIdx.add(remainingUnmergedMXEIdx.get(0));
                    remainingUnmergedMXEIdx.remove(0);
                }
                
                int numChecked=0;
                while(numChecked<toBeMergedIdx.size()){
                    ArrayList<Integer> remainingUnmergedBeforeCheck=new ArrayList<>();
                    remainingUnmergedBeforeCheck.addAll(remainingUnmergedMXEIdx);
                    
                    int idx1=toBeMergedIdx.get(numChecked);
                    for(int idx2 : remainingUnmergedBeforeCheck)
                        if(merged[idx1][idx2] && !toBeMergedIdx.contains(idx2)){
                            //System.err.println(idx1+"\t"+idx2);
                            toBeMergedIdx.add(idx2);
                            remainingUnmergedMXEIdx.remove(new Integer(idx2));
                        }
                    
                    numChecked++;
                }
                
                // check whether the events in the to-be-merged list are globally compatible
                int[] extrema={-1,-1,-1,-1,-1,-1};
                for(int idx : toBeMergedIdx){
                    ArrayList<Junction> juncEvent=mxeEventsBeforeMerge.get(idx);
                    extrema[0]=(extrema[0]<juncEvent.get(0).getStartSite()) ? juncEvent.get(0).getStartSite() : extrema[0];
                    extrema[1]=(extrema[1]==-1 || juncEvent.get(0).getEndSite()<extrema[1]) ? juncEvent.get(0).getEndSite() : extrema[1];
                    extrema[2]=(extrema[2]<juncEvent.get(2).getStartSite()) ? juncEvent.get(2).getStartSite() : extrema[2];
                    extrema[3]=(extrema[3]==-1 || juncEvent.get(1).getEndSite()<extrema[3]) ? juncEvent.get(1).getEndSite() : extrema[3];
                    extrema[4]=(extrema[4]<juncEvent.get(3).getStartSite()) ? juncEvent.get(3).getStartSite() : extrema[4];
                    extrema[5]=(extrema[5]==-1 || juncEvent.get(3).getEndSite()<extrema[5]) ? juncEvent.get(3).getEndSite() : extrema[5];
                }
                boolean compatible=true;
                compatible=compatible && extrema[0]<extrema[1] && extrema[1]<extrema[2] && extrema[2]<extrema[3];
                compatible=compatible && extrema[3]<extrema[4] && extrema[4]<extrema[5];
                if(compatible){ // can be merged, then merge
                    //System.err.println(toBeMergedIdx.toString());
                    ArrayList<Junction> incluP1=new ArrayList<>();
                    ArrayList<Junction> incluP2=new ArrayList<>();
                    ArrayList<Junction> excluP1=new ArrayList<>();
                    ArrayList<Junction> excluP2=new ArrayList<>();
                    for(int idx : toBeMergedIdx){
                        ArrayList<Junction> juncEvent=mxeEventsBeforeMerge.get(idx);
                        //System.err.println(juncEvent.toString());
                        if(!incluP1.contains(juncEvent.get(0))) incluP1.add(juncEvent.get(0));
                        if(!incluP2.contains(juncEvent.get(2))) incluP2.add(juncEvent.get(2));
                        if(!excluP1.contains(juncEvent.get(1))) excluP1.add(juncEvent.get(1));
                        if(!excluP2.contains(juncEvent.get(3))) excluP2.add(juncEvent.get(3));
                    }
                    
                    ASEvent newEvent=new ASEvent(ASEvent.MUTUAL_EXCL, incluP1, incluP2, excluP1, excluP2);
                    eventsInType.get(ASEvent.MUTUAL_EXCL).add(newEvent);
                    eventsInTypeThisGroup.get(ASEvent.MUTUAL_EXCL).add(newEvent);
                    events.add(newEvent);
                }
                toBeMergedIdx.clear();
            }
            //System.err.println("merged mxe events for group "+group);
            
            for(int i=0; i<eventsInTypeThisGroup.size(); i++)
                eventsInTypeInGroup.get(i).put(group, eventsInTypeThisGroup.get(i));
        }
    }
    
    public void outputASEventSet(){
        String[] typeName={"SKIPPED_EXON","MUTUAL_EXCLU_EXON","ALT_END"};
        for(int i=0; i<eventsInTypeInGroup.size(); i++){
            TreeSet<String> sortedGroups=new TreeSet<>(new Comparator<String>(){
                @Override
                public int compare(String arg0, String arg1){
                    return(arg0.compareTo(arg1));
                }
            });
            sortedGroups.addAll(eventsInTypeInGroup.get(i).keySet());
            
            for(Iterator<String> it1=sortedGroups.iterator(); it1.hasNext(); ){
                String group=it1.next();
                for(ASEvent event : eventsInTypeInGroup.get(i).get(group)){
                    TreeSet<Junction> sortedIncluJuncs=new TreeSet<>(new Comparator<Junction>(){
                        @Override
                        public int compare(Junction arg0, Junction arg1) {
                            return(arg0.compareTo(arg1));
                        }
                    });
                    sortedIncluJuncs.addAll(event.getInclusiveJunctions());
                    String inclus="";
                    for(Iterator<Junction> it=sortedIncluJuncs.iterator(); it.hasNext();){
                        Junction junc=it.next();
                        inclus+=junc.toString()+";";
                    }

                    TreeSet<Junction> sortedExcluJuncs=new TreeSet<>(new Comparator<Junction>(){
                    @Override
                    public int compare(Junction arg0, Junction arg1){
                        return(arg0.compareTo(arg1));
                    }
                    });
                    sortedExcluJuncs.addAll(event.getExclusiveJunctions());
                    String exclus="";
                    for(Iterator<Junction> it=sortedExcluJuncs.iterator(); it.hasNext();){
                        Junction junc=it.next();
                        exclus+=junc.toString()+";";
                    }

                    System.out.println(group+"\t"+typeName[i]+"\t"+inclus+"\t"+exclus);
                }
            }
        }
    }
}
