/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hitseq;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;
import net.sf.samtools.*;

/**
 *
 * @author hezhisong
 */
public class JunctionSet {
    private HashMap<String,HashSet<Junction>> juncInChrom;
    
    JunctionSet(){
        juncInChrom=new HashMap<>();
    }
    
    JunctionSet(File file, String fileType){
        this();
        addJunctionSet(file, fileType);
    }
    
    JunctionSet(Annotation annotation){
        juncInChrom=annotation.getAllJunctionsInChrom();
    }
    
    /**
     * FunName: addJunctionSet.
     * Description: The method to add additional junctions in the given file to the junction set.
     * The existed undirected junctions would be replaced if directed junctions with the same coordinate are available in the new set.
     * @param file File object of the input file
     * @param fileType File type of the input file, should be one of "bam" (will be seen as no-strand), "gtf", "bed" and "juncs"
     */
    final void addJunctionSet(File file, String fileType){
        if(! file.exists()){
            System.err.println("Cannot find file "+file.getAbsolutePath()+".");
            System.exit(1);
        }
        
        if(fileType.equalsIgnoreCase("bam")){ // junctions in BAM file, no strand information
            addJunctionSet(file,0);
            
        } else if(fileType.equalsIgnoreCase("gtf")){ // junctions annotated as gene structure in GTF file
            Annotation annotation=new Annotation(file, "gtf");
            System.err.println("generate junctions from annotation...");
            HashMap<String,HashSet<Junction>> newJuncInChrom=annotation.getAllJunctionsInChrom();
            
            for(String chrom : newJuncInChrom.keySet()){
                if(!juncInChrom.containsKey(chrom))
                    juncInChrom.put(chrom, newJuncInChrom.get(chrom));
                else{
                    for(Junction junc : newJuncInChrom.get(chrom)){
                        Junction juncNoStrand=new Junction(junc.getID(), junc.getChrom(), "*", junc.getDonorSite(), junc.getAcceptorSite());
                        if(juncInChrom.get(chrom).contains(junc))
                            juncInChrom.get(chrom).remove(junc);
                        if(juncInChrom.get(chrom).contains(juncNoStrand))
                            juncInChrom.get(chrom).remove(juncNoStrand);
                        juncInChrom.get(chrom).add(junc);
                    }
                }
            }
            
        } else{ // junctions annotated in BED/JUNC file
            try{
                RandomAccessFile fileIn=new RandomAccessFile(file,"r");
                int numLines=0;
                String line;
                
                boolean refreshJuncSet=!juncInChrom.isEmpty();
                while((line=fileIn.readLine()) != null){ // deal with each line separately
                    if(line.startsWith("#"))
                        continue;
                    numLines++;

                    String[] elements=line.split("\t");
                    if(elements.length<3)
                        continue;
                    switch (fileType) {
                        case "bed": 
                            String chrom=elements[0];
                            String strand=elements.length>5 ? elements[5] : "*";
                            int donorSite;
                            int acceptorSite;
                            Junction newJunction;
                            Junction newJunctionNoStrand;
                            
                            if(elements.length<7){ // Fields: chrom, donor(3'end of last exon), acceptor(5'end of next exon), id, score, strand
                                donorSite=Integer.parseInt(elements[1])+1;
                                acceptorSite=Integer.parseInt(elements[2]);
                                String id=elements.length>3 ? elements[3] : "junc"+numLines;
                                
                                newJunction=new Junction(id,chrom,strand,donorSite,acceptorSite);
                            } else{ // Output of TopHat2. Fields: chrom, 5'anchor, 3'anchor, id, score, strand, 5'anchor, 3'anchor, color, ??, block size, ref size
                                String[] blockSize=elements[10].split(",");
                                donorSite=Integer.parseInt(elements[1])+Integer.parseInt(blockSize[0]);
                                acceptorSite=Integer.parseInt(elements[2])-Integer.parseInt(blockSize[1])+1;
                                
                                newJunction=new Junction(chrom,strand,donorSite,acceptorSite);
                            }
                            if(!juncInChrom.containsKey(chrom))
                                    juncInChrom.put(chrom, new HashSet<Junction>());
                            
                            if(refreshJuncSet && !strand.equals("*")){
                                newJunctionNoStrand=new Junction(chrom,"*",donorSite,acceptorSite);
                                juncInChrom.get(chrom).remove(newJunctionNoStrand);
                            }
                            juncInChrom.get(chrom).add(newJunction);
                            
                            break;
                        case "juncs": // Fields: chrom, donor(count from 0), acceptor(count from 0), strand
                            chrom=elements[0];
                            donorSite=Integer.parseInt(elements[1])+1;
                            acceptorSite=Integer.parseInt(elements[2])+1;
                            strand=elements.length>3 ? elements[3] : "*";

                            if(!juncInChrom.containsKey(chrom))
                                juncInChrom.put(chrom, new HashSet<Junction>());
                            
                            if(!strand.equals("*") && refreshJuncSet){
                                Junction newJuncNoStrand=new Junction(chrom,"*",donorSite,acceptorSite);
                                juncInChrom.get(chrom).remove(newJuncNoStrand);
                            }
                            juncInChrom.get(chrom).add(new Junction(chrom,strand,donorSite,acceptorSite));
                            break;
                    }
                }
            }
            catch(IOException | NumberFormatException e){
                System.err.println("Error in JunctionSet.java: "+e);
            }
        }
    }
    
    /**
     * FunName: addJunctionSet.
     * Description: The specific addJunctionSet method for BAM file input.
     * If the BAM file has no strand information, all junctions will be seen as undirected.
     * @param file File object of the input file
     * @param strandSpecific Strand mode of the BAM file. 0 for no strand, 1 for the same strand, -1 for the opposite strand.
     */
    final void addJunctionSet(File file, int strandSpecific){
        try (SAMFileReader inputSam = new SAMFileReader(file)) {
            int numJunctionReads=0, numMappedReads=0;
            for(SAMRecord record : inputSam){
                if(record.getReadUnmappedFlag()) // skip if this read is unmapped
                    continue;
                if(record.getReadPairedFlag() && (! record.getProperPairFlag())) // skip if the read if paired but not in the proper paired mapping
                    continue;

                numMappedReads++;
                if(numMappedReads%1000000==0)
                    System.err.println("reading reads "+numMappedReads+"...");

                List<AlignmentBlock> hitsList=record.getAlignmentBlocks();
                ArrayList<AlignmentBlock> hits=new ArrayList<>();
                for(AlignmentBlock block : hitsList)
                    hits.add(block);

                String chrom=record.getReferenceName();
                String strand;
                if(strandSpecific==0)
                    strand="*";
                else if(strandSpecific==1)
                    strand=record.getReadNegativeStrandFlag() ? "-" : "+";
                else
                    strand=record.getReadNegativeStrandFlag() ? "+" : "-";

                Cigar cigar=record.getCigar();
                if(cigar.numCigarElements()==1)
                    continue;

                int lastBlockEnd=hits.get(0).getReferenceStart()-1;
                boolean containJunction=false;
                for(int i=0; i<cigar.numCigarElements(); i++){
                    CigarElement element=cigar.getCigarElement(i);
                    if(element.getOperator().consumesReferenceBases()){
                        int thisBlockEnd=lastBlockEnd+element.getLength();
                        if(element.getOperator().equals(CigarOperator.N)){
                            Junction junction=new Junction(chrom, strand, lastBlockEnd, thisBlockEnd+1);
                            if(!juncInChrom.containsKey(chrom))
                                juncInChrom.put(chrom, new HashSet<Junction>());
                            
                            if(strandSpecific!=0){
                                Junction juncNoStrand=new Junction(chrom,"*",lastBlockEnd, thisBlockEnd+1);
                                juncInChrom.get(chrom).remove(juncNoStrand);
                                juncInChrom.get(chrom).add(junction);
                            } else{
                                Junction juncPos=new Junction(chrom,"+",lastBlockEnd, thisBlockEnd+1);
                                Junction juncNeg=new Junction(chrom,"-",lastBlockEnd, thisBlockEnd+1);
                                if(!juncInChrom.get(chrom).contains(juncPos) || juncInChrom.get(chrom).contains(juncNeg))
                                    juncInChrom.get(chrom).add(junction);
                            }
                            containJunction=true;
                        }
                        lastBlockEnd=thisBlockEnd;
                    }
                }

                if(containJunction)
                    numJunctionReads++;
            }

            System.err.println("");
        }
        catch(Exception e){
            System.err.println("Error in JunctionSet.java when reading BAM: "+e);
            System.exit(1);
        }
    }
    
    /**
     * FunName: annotateJuncSet.
     * Description: Given a GTF file as input, to annotate the existed junctions in the current junction set.
     * @param file The file object of the GTF input file.
     */
    void annotateJuncSet(File file){
        Annotation annotation=new Annotation(file, "gtf");
        System.err.println("generate junctions from annotation...");
        HashMap<String,HashSet<Junction>> annotatedJuncInChrom=annotation.getAllJunctionsInChrom();
        
        for(String chrom : juncInChrom.keySet()){
            if(!annotatedJuncInChrom.containsKey(chrom))
                continue;
            
            for(Junction junc : annotatedJuncInChrom.get(chrom)){
                if(juncInChrom.get(chrom).remove(junc))
                    juncInChrom.get(chrom).add(junc);
            }
        }
    }
    
    HashMap<String, HashSet<Junction>> getJunctions(){
        return(juncInChrom);
    }
    
    /**
     * FunName: outputInJuncs.
     * Description: Output the junction set in the modified "juncs" format to STOUT.
     * @param outputGene If true, the genes that the junction belongs to (if there is any) will be also output at the 5th column.
     */
    void outputInJuncs(boolean outputGene){
        java.util.TreeSet<String> sortedChromNames=new java.util.TreeSet<>(new java.util.Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        });
        sortedChromNames.addAll(juncInChrom.keySet());
        for (Iterator<String> it = sortedChromNames.iterator(); it.hasNext();) {
            String chrom = it.next();
            java.util.TreeSet<Junction> sortedJunctions=new java.util.TreeSet<>(new java.util.Comparator<Junction>() {
                @Override
                public int compare(Junction o1, Junction o2) {
                    return o1.compareTo(o2);
                }
            });
            sortedJunctions.addAll(juncInChrom.get(chrom));
            
            for(Iterator<Junction> it2=sortedJunctions.iterator(); it2.hasNext();){
                Junction junc=it2.next();
                String strand=junc.getStrand();
                int donorSite=junc.getDonorSite();
                int acceptorSite=junc.getAcceptorSite();
                
                if(outputGene){
                    HashSet<Gene> linkedGenes=junc.getAnnotatedGenes();
                    String annotation="";
                    for(Gene gene : linkedGenes){
                        String geneID=gene.getID();
                        if(!annotation.isEmpty()) annotation+=";";
                        annotation+=geneID;
                    }
                    System.out.println(chrom+"\t"+(donorSite-1)+"\t"+(acceptorSite-1)+"\t"+strand+"\t"+annotation);
                }
                else
                    System.out.println(chrom+"\t"+(donorSite-1)+"\t"+(acceptorSite-1)+"\t"+strand);
            }
        }
    }
}
