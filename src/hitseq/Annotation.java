/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hitseq;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author hezhisong
 */
public class Annotation {
    private HashMap<String, ArrayList<String>> genesInChrom;
    private HashMap<String, Gene> allGenes;
    private HashMap<Gene, Integer> lengthOfGene;
    private HashMap<Gene, Integer> exclusiveLengthOfGene;
    private HashMap<Gene, Integer> exclusiveLengthOfGeneNoStrand;
    
    private HashMap<String, Integer> pointerOfChrom;
    private HashMap<Gene, Integer> pointerOfGene;
    
    public Annotation(){
        reset();
    }
    
    public Annotation(File file, String fileType){
        reset();
        addAdditionalAnnotations(file, fileType);
    }
    
    public Annotation(File file){
        this(file, "struc");
    }
    
    final void reset(){
        resetAnnotation();
        resetPointer();
    }
    
    /**
     * FunName: resetAnnotation.
     * Description: Empty the annotation set.
     */
    final void resetAnnotation(){
        genesInChrom=new HashMap<>();
        allGenes=new HashMap<>();
        lengthOfGene=new HashMap<>();
        exclusiveLengthOfGene=new HashMap<>();
        exclusiveLengthOfGeneNoStrand=new HashMap<>();
    }
    
    /**
     * FunName: resetPointer.
     * Description: Set all gene pointers at chromosomes as well as all exon pointers at genes to 0.
     */
    final void resetPointer(){
        pointerOfChrom=new HashMap<>();
        pointerOfGene=new HashMap<>();
        if(! genesInChrom.isEmpty())
            for(String chrom : genesInChrom.keySet()){
                pointerOfChrom.put(chrom, 0);
                if(!genesInChrom.isEmpty())
                    for(String gene : genesInChrom.get(chrom))
                        pointerOfGene.put(allGenes.get(gene), 0);
            }
    }
    
    /**
     * FunName: addAdditionalAnnotations.
     * Description: Add additional gene/transcript/exon information in the given annotation file to the annotation set.
     * @param file The File object of the new annotation file.
     * @param fileType The file type of the given file. Should be one of "struc", "gtf" and "bed" (only for BED8).
     */
    final void addAdditionalAnnotations(File file, String fileType){
        try{
            RandomAccessFile fileIn=new RandomAccessFile(file,"r");
            
            // Read the annotation file
            int numLines=0;
            String line;
            while((line=fileIn.readLine()) != null){ // deal with each line separately
                if(line.startsWith("#"))
                    continue;
                String[] elements=line.split("\t");
                switch (fileType) {
                    case "struc":
                        // File format: gene structure (my customized file type)
                        // Fields: geneID, chrom, strand, exonLength, exonStructure
                        
                        if(! genesInChrom.containsKey(elements[1])){
                            genesInChrom.put(elements[1], new ArrayList<String>());
                            pointerOfChrom.put(elements[1], 0);
                        }
                        
                        Integer geneStart=-1;
                        Integer geneEnd=-1;
                        Pattern geneStartPattern=Pattern.compile("^\\d+");
                        Pattern geneEndPattern=Pattern.compile("\\d+$");
                        Matcher geneStartMatcher=geneStartPattern.matcher(elements[4]);
                        Matcher geneEndMatcher=geneEndPattern.matcher(elements[4]);
                        if(geneStartMatcher.find() && geneEndMatcher.find()){
                            geneStart=Integer.valueOf(geneStartMatcher.group());
                            geneEnd=Integer.valueOf(geneEndMatcher.group());
                        }
                        switch (elements[2]) {
                            case "1":
                                elements[2]="+";
                                break;
                            case "-1":
                                elements[2]="-";
                                break;
                        }
                        
                        Gene gene;
                        if(allGenes.containsKey(elements[0]))
                            gene=allGenes.get(elements[0]);
                        else{
                            gene=new Gene(elements[0], elements[1], elements[2], geneStart, geneEnd);
                            genesInChrom.get(elements[1]).add(elements[0]);
                            allGenes.put(elements[0], gene);
                            pointerOfGene.put(gene, 0);
                            lengthOfGene.put(gene, Integer.valueOf(elements[3]));
                        }
                        
                        String transcriptID=elements[0];
                        if(gene.containTranscript(transcriptID)){
                            int idx=1;
                            while(gene.containTranscript(transcriptID+String.valueOf(idx)))
                                idx++;
                            transcriptID=transcriptID+String.valueOf(idx);
                        }
                        Transcript newTranscript=new Transcript(transcriptID, elements[1], elements[2], geneStart, geneEnd);
                        
                        String[] exons=elements[4].split(",");
                        for(String exon : exons){
                            String[] coord=exon.split("\\.\\.");
                            Exon newExon=new Exon(elements[1], elements[2], Integer.valueOf(coord[0]), Integer.valueOf(coord[1]));
                            newTranscript.addExon(newExon);
                        }
                        
                        gene.addTranscript(newTranscript);
                        gene.generateNonredundantTranscript();
                        lengthOfGene.put(gene, gene.getTotalExonLength());
                        
                        break;
                    case "gtf":
                        // File format: GTF file from Ensembl
                        if(elements[2].equals("exon")){
                            if(! genesInChrom.containsKey(elements[0])){
                                genesInChrom.put(elements[0], new ArrayList<String>());
                                pointerOfChrom.put(elements[0], 0);
                            }
                            Integer exonStart=Integer.valueOf(elements[3]);
                            Integer exonEnd=Integer.valueOf(elements[4]);
                            
                            Pattern geneIdPattern=Pattern.compile("gene_id \"[\\w\\.]+\";");
                            Matcher geneIdMatcher=geneIdPattern.matcher(elements[8]);
                            Pattern transcriptIdPattern=Pattern.compile("transcript_id \"[\\w\\.]+\";");
                            Matcher transcriptIdMatcher=transcriptIdPattern.matcher(elements[8]);
                            
                            if(geneIdMatcher.find() && transcriptIdMatcher.find()){
                                String geneId=Pattern.compile("gene_id \"").matcher(geneIdMatcher.group()).replaceAll("");
                                geneId=Pattern.compile("\";").matcher(geneId).replaceAll("");
                                String transcriptId=Pattern.compile("transcript_id \"").matcher(transcriptIdMatcher.group()).replaceAll("");
                                transcriptId=Pattern.compile("\";").matcher(transcriptId).replaceAll("");
                                
                                if(allGenes.containsKey(geneId))
                                    gene=allGenes.get(geneId);
                                else{
                                    gene=new Gene(geneId, elements[0], elements[6]);
                                    genesInChrom.get(elements[0]).add(geneId);
                                    allGenes.put(geneId, gene);
                                    pointerOfGene.put(gene, 0);
                                }
                                
                                
                                Transcript transcript;
                                if(gene.containTranscript(transcriptId))
                                    transcript=gene.getTranscript(transcriptId);
                                else{
                                    transcript=new Transcript(transcriptId, elements[0], elements[6]);
                                    gene.addTranscript(transcript);
                                }
                                
                                Exon newExon=new Exon(elements[0], elements[6], exonStart, exonEnd);
                                transcript.addExon(newExon);
                            }
                        }
                        break;
                    case "bed":
                        // File format: BED4/BED8
                        // Fields: chrom, chromStart, chromEnd, name, score, strand; the other fields will not be considered.
                        if(elements.length<3)
                            break;
                        
                        if(! genesInChrom.containsKey(elements[0])){
                            genesInChrom.put(elements[0], new ArrayList<String>());
                            pointerOfChrom.put(elements[0], 0);
                        }
                        Integer exonStart=Integer.valueOf(elements[1])+1;
                        Integer exonEnd=Integer.valueOf(elements[2]);
                        String name=elements.length>3 ? elements[3] : "Interval."+numLines;
                        String strand=elements.length>5 ? elements[5] : "*";
                        
                        if(allGenes.containsKey(name)){
                            int idx=1;
                            while(allGenes.containsKey(name+String.valueOf(idx)))
                                idx++;
                            name=name+String.valueOf(idx);
                        }
                        gene=new Gene(name, elements[0], strand, exonStart, exonEnd);
                        Transcript transcript=new Transcript(name, elements[0], strand, exonStart, exonEnd);
                        transcript.addExon(new Exon(elements[0], strand, exonStart, exonEnd));
                        gene.addTranscript(transcript);
                        gene.generateNonredundantTranscript();
                        
                        genesInChrom.get(elements[0]).add(name);
                        allGenes.put(name, gene);
                        lengthOfGene.put(gene, Integer.valueOf(exonEnd-exonStart+1));
                        
                        break;
                }
                numLines++;
                if(numLines%100000==0)
                    System.err.println("read annotation (in "+fileType+") "+String.valueOf(numLines)+" lines");
            }
            
            System.err.println("finish reading annotation file (in "+fileType+" format)");
            
            
            if(fileType.equals("gtf")){
                // If the annotation is in GTF format, or the operation is to add additional annotation, the overlapping exons of the same gene should be merged
                System.err.println("start sorting gene structure");
                System.err.println("in total "+allGenes.size()+" genes");
                for(Gene gene : allGenes.values()){
                    int length=gene.getTotalExonLength(); // generate non-redundant transcript for the gene, as well as gene length
                    lengthOfGene.put(gene, length);
                }
            }
            
            // sort the gene list for each chromosome
            for(String chrom : genesInChrom.keySet()){
                if(genesInChrom.get(chrom).size()==1) // if there is only one gene in the set, sorting is not necessary
                    continue;
                
                for(int i=0; i<genesInChrom.get(chrom).size()-1; i++){ // i: last index of ordered area
                    int j=i+1; // j: first index of unordered area
                    String geneFirstUnordered=genesInChrom.get(chrom).get(j);
                    
                    while(i>=0 && (allGenes.get(genesInChrom.get(chrom).get(i)).getStart() > allGenes.get(geneFirstUnordered).getStart() || (allGenes.get(genesInChrom.get(chrom).get(i)).getStart() == allGenes.get(geneFirstUnordered).getStart() && allGenes.get(genesInChrom.get(chrom).get(i)).getEnd() > allGenes.get(geneFirstUnordered).getEnd()))){
                        i--;
                    }
                    genesInChrom.get(chrom).add(i+1, geneFirstUnordered);
                    genesInChrom.get(chrom).remove(j+1);
                    i=j-1;
                }
            }
        }
        catch(java.io.IOException e){
            System.err.println("IO error: cannot open file"+file.getPath());
        }
    }
    
    /**
     * FunName: outputInStruc.
     * Description: Output the annotation set to STOUT in struc format.
     */
    void outputInStruc(){
        // Fields: geneID, chrom, strand, exonLength, exonStructure
        for(String chr : genesInChrom.keySet()){
            for(String geneID : genesInChrom.get(chr)){
                Gene gene=allGenes.get(geneID);
                String strand=gene.getStrand();
                strand=strand.equals("+") ? "1" : "-1";
                Integer length=lengthOfGene.get(gene);
                //System.err.println(gene+"\t"+length);
                
                Transcript nonredundant=gene.getNonredundantTranscript();
                String struc="";
                for(int i=0; i<nonredundant.getExons().size(); i++){
                    if(! struc.isEmpty())
                        struc=struc+",";
                    String start=String.valueOf(nonredundant.getExons().get(i).getStart());
                    String end=String.valueOf(nonredundant.getExons().get(i).getEnd());
                    struc=struc+start+".."+end;
                }
                
                System.out.println(gene.getID()+"\t"+chr+"\t"+strand+"\t"+length.toString()+"\t"+struc);
            }
        }
    }
    
    /**
     * FunName: outputInStruc.
     * Description: Output the annotation set to the given file in struc format.
     * @param file The File object for output.
     */
    void outputInStruc(File file){
        try{
            RandomAccessFile fileOut=new RandomAccessFile(file,"w");
            for(String chr : genesInChrom.keySet()){
                for(String geneID : genesInChrom.get(chr)){
                    Gene gene=allGenes.get(geneID);
                    String strand=gene.getStrand();
                    strand=strand.equals("+") ? "1" : "-1";
                    Integer length=lengthOfGene.get(gene);
                    //System.err.println(gene+"\t"+length);

                    Transcript nonredundant=gene.getNonredundantTranscript();
                    String struc="";
                    for(int i=0; i<nonredundant.getExons().size(); i++){
                        if(! struc.isEmpty())
                            struc=struc+",";
                        String start=String.valueOf(nonredundant.getExons().get(i).getStart());
                        String end=String.valueOf(nonredundant.getExons().get(i).getEnd());
                        struc=struc+start+".."+end;
                    }

                    fileOut.writeUTF(gene+"\t"+chr+"\t"+strand+"\t"+length.toString()+"\t"+struc+System.getProperty("line.separator"));
                }
            }
        } catch(java.io.IOException e){
            System.err.println("IO error: cannot open file"+file.getPath());
        }
    }
    
    /**
     * FunName: chromIsExisted.
     * Description: Check whether the given chromosome existed in the current annotation set.
     * @param chrom Name of the chromosome.
     * @return true if the chromosome exists.
     */
    boolean chromIsExisted(String chrom){
        return genesInChrom.containsKey(chrom);
    }
    
    String[] getAvailableChromosomes(){
        return (String[])genesInChrom.keySet().toArray();
    }
    
    String[] getGeneSet(){
        Object[] results=allGenes.keySet().toArray();
        String[] answer=new String[results.length];
        for(int i=0; i<results.length; i++)
            answer[i]=(String) results[i];
        return answer;
    }
    
    String[] getGeneSet(String chrom){
        if(genesInChrom.containsKey(chrom))
            return (String[]) genesInChrom.get(chrom).toArray();
        else
            return null;
    }
    
    int getCurrentGenePointer(String chrom){
        if(genesInChrom.containsKey(chrom))
            return pointerOfChrom.get(chrom);
        else
            return -1;
    }
    
    String getGene(String chrom, int pointer){
        if(genesInChrom.containsKey(chrom) && genesInChrom.get(chrom).size()>pointer)
            return genesInChrom.get(chrom).get(pointer);
        else
            return null;
    }
    
    Gene getGene(String geneID){
        if(allGenes.containsKey(geneID))
            return(allGenes.get(geneID));
        else
            return null;
    }
    
    String getGeneStrand(String gene){
        if(allGenes.containsKey(gene))
            return allGenes.get(gene).getStrand();
        else
            return null;
    }
    
    int getGeneLength(String gene){
        if(allGenes.containsKey(gene))
            return lengthOfGene.get(allGenes.get(gene));
        else
            return -1;
    }
    
    int getExclusiveGeneLength(String gene){
        if(allGenes.containsKey(gene))
            return exclusiveLengthOfGene.get(allGenes.get(gene));
        else
            return -1;
    }
    
    int getExclusiveGeneLengthNoStrand(String gene){
        if(allGenes.containsKey(gene))
            return exclusiveLengthOfGeneNoStrand.get(allGenes.get(gene));
        else
            return -1;
    }
    
    int getGeneStart(String gene){
        if(allGenes.containsKey(gene))
            return allGenes.get(gene).getStart();
        else
            return -1;
    }
    
    int getGeneEnd(String gene){
        if(allGenes.containsKey(gene))
            return allGenes.get(gene).getEnd();
        else
            return -1;
    }
    
    int getCurrentExonIndex(String gene){
        if(allGenes.containsKey(gene))
            return pointerOfGene.get(allGenes.get(gene));
        else
            return -1;
    }
    
    int getNonRedundantExonStart(String geneID, int index){
        if(allGenes.containsKey(geneID)){
            Gene gene=allGenes.get(geneID);
            if(index < gene.getNonredundantTranscript().getExons().size() && index >=0)
                return gene.getNonredundantTranscript().getExons().get(index).getStart();
        }
        return -1;
    }
    
    int getNonRedundantExonEnd(String geneID, int index){
        if(allGenes.containsKey(geneID)){
            Gene gene=allGenes.get(geneID);
            if(index < gene.getNonredundantTranscript().getExons().size() && index >=0)
                return gene.getNonredundantTranscript().getExons().get(index).getEnd();
        }
        return -1;
    }
    
    int movePointerGene(String chrom){
        if(pointerOfChrom.get(chrom)>=genesInChrom.get(chrom).size())
            pointerOfChrom.put(chrom, -1);
        else
            pointerOfChrom.put(chrom, pointerOfChrom.get(chrom)+1);
        return pointerOfChrom.get(chrom);
    }
    
    int movePointerExon(String gene){
        pointerOfGene.put(allGenes.get(gene), pointerOfGene.get(allGenes.get(gene))+1);
        return pointerOfGene.get(allGenes.get(gene));
    }
    
    /**
     * FunName: estimateExclusiveGeneLength.
     * Description: Detect overlap among genes to calculate the exclusive exon length of every gene for both consider or not the strand.
     * @param outputPairs if true, output the gene pairs with overlapping regions (even on different strands).
     */
    void estimateExclusiveGeneLength(boolean outputPairs){
        for(Gene gene : lengthOfGene.keySet()){
            exclusiveLengthOfGene.put(gene, lengthOfGene.get(gene));
            exclusiveLengthOfGeneNoStrand.put(gene, lengthOfGene.get(gene));
        }
        
        for(String chrom : genesInChrom.keySet()){
            ArrayList<String> genesInThisChrom=genesInChrom.get(chrom);
            for(int i=0; i<genesInThisChrom.size()-1; i++){
                Gene thisGene=allGenes.get(genesInThisChrom.get(i));
                int geneStartThis=thisGene.getStart();
                int geneEndThis=thisGene.getEnd();

                ArrayList<Gene> overlapGenes=new ArrayList<>();
                for(int j=i+1; j<genesInThisChrom.size(); j++){
                    Gene thatGene=allGenes.get(genesInThisChrom.get(j));
                    int geneStartThat=thatGene.getStart();
                    if(geneStartThis <= geneStartThat && geneStartThat <= geneEndThis)
                        overlapGenes.add(thatGene);
                    else if(geneEndThis < geneStartThat)
                        break;
                }
                
                //if(overlapGenes.size()>0) System.out.println("overlap candidates for "+thisGene+": "+overlapGenes);
                for(Gene thatGene : overlapGenes){
                    boolean sameStrand=thisGene.getStrand().equals(thatGene.getStrand());
                    int overlapSize=0;
                    int overlapSizeNoStrand=0;
                    
                    ArrayList<Exon> overlapRegionsWithStrand=thisGene.getOverlappingRegion(thatGene, true);
                    ArrayList<Exon> overlapRegionsNoStrand=thisGene.getOverlappingRegion(thatGene, false);
                    
                    for(Exon exon : overlapRegionsWithStrand)
                        overlapSize+=exon.getLength();
                    for(Exon exon : overlapRegionsNoStrand)
                        overlapSizeNoStrand+=exon.getLength();
                    
                    exclusiveLengthOfGene.put(thisGene, exclusiveLengthOfGene.get(thisGene).intValue()-overlapSize);
                    exclusiveLengthOfGeneNoStrand.put(thisGene, exclusiveLengthOfGeneNoStrand.get(thisGene).intValue()-overlapSizeNoStrand);
                    exclusiveLengthOfGene.put(thatGene, exclusiveLengthOfGene.get(thatGene).intValue()-overlapSize);
                    exclusiveLengthOfGeneNoStrand.put(thatGene, exclusiveLengthOfGeneNoStrand.get(thatGene).intValue()-overlapSizeNoStrand);

                    if(outputPairs && overlapSizeNoStrand>0)
                        System.out.println(thisGene+"\t"+thatGene+"\t"+sameStrand+"\t"+overlapSizeNoStrand+"\t"+overlapSize);
                }
                //System.out.println("done "+i+" genes in "+chrom);
            }
        }
    }
    
    /**
     * FunName: estimateExclusiveGeneLength.
     * Description: Detect overlap among genes to calculate the exclusive exon length of every gene for both consider or not the strand.
     * Here, the overlapping pairs will not be output.
     */
    void estimateExclusiveGeneLength(){
        estimateExclusiveGeneLength(false);
    }
    
    /**
     * FunName: getAllJunctionsInChrom.
     * Description: Get all the possible junctions based on the current annotation set, and organize junctions at the same chromosome into a HashSet.
     * @return The junctions annotated by the current annotation set, organized by chromosomes.
     */
    HashMap<String,HashSet<Junction>> getAllJunctionsInChrom(){
        HashMap<String,HashSet<Junction>> junctions=new HashMap<>();
        for(String chrom : genesInChrom.keySet()){
            junctions.put(chrom, new HashSet<Junction>());
            for(String geneID : genesInChrom.get(chrom)){
                Gene gene=allGenes.get(geneID);
                gene.generateAllJunctions();
                
                ArrayList<Junction> junctionSetGene=new ArrayList<>();
                junctionSetGene.addAll(gene.getAllJunctions());
                for(Junction junc : junctionSetGene){
                    if(junctions.get(chrom).contains(junc)){
                        for(Junction juncExisted : junctions.get(chrom))
                            if(juncExisted.equals(junc)){
                                juncExisted.addAnnotatedGeneSet(junc.getAnnotatedGenes());
                                juncExisted.addAnnotatedTranscriptSet(junc.getAnnotatedTranscripts());
                                gene.getAllJunctions().remove(junc);
                                gene.getAllJunctions().add(juncExisted);
                            }
                    } else
                        junctions.get(chrom).add(junc);
                }
            }
            System.err.println("Chromosome "+chrom+": "+junctions.get(chrom).size()+" junctions");
        }
        return(junctions);
    }
}
