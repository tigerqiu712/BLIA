/**
 * Copyright (c) 2014 by Software Engineering Lab. of Sungkyunkwan University. All Rights Reserved.
 * 
 * Permission to use, copy, modify, and distribute this software and its documentation for
 * educational, research, and not-for-profit purposes, without fee and without a signed licensing agreement,
 * is hereby granted, provided that the above copyright notice appears in all copies, modifications, and distributions.
 */
package edu.skku.selab.blp.blia.indexer;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import edu.skku.selab.blp.Property;
import edu.skku.selab.blp.common.SourceFileCorpus;
import edu.skku.selab.blp.db.AnalysisValue;
import edu.skku.selab.blp.db.dao.SourceFileDAO;

/**
 * @author Klaus Changsun Youm(klausyoum@skku.edu)
 *
 */
public class SourceFileVectorCreator {
	private HashMap<String, Integer> totalCorpusLengths = null;
	private HashMap<String, SourceFileCorpus> sourceFileCorpusMap = null;
	private int fileCount = 0; 
	

    public SourceFileVectorCreator() {
    }
    
	/**
	 * Calculate document counts from source code corpus data
	 *  
	 * @return Hashtable<String, Integer>	Corpus, Term Count 
	 * @throws IOException
	 */
	public Hashtable<String, Integer> getInverseDocCountTable(String version) throws Exception {
		SourceFileDAO sourceFileDAO = new SourceFileDAO();
		HashMap<String, SourceFileCorpus> corpusSets = sourceFileDAO.getCorpusMap(version);
		
		Iterator<String> fileNameIter = corpusSets.keySet().iterator();
		Hashtable<String, Integer> countTable = new Hashtable<String, Integer>();
		
		while(fileNameIter.hasNext()) {
			String fileName = fileNameIter.next();
			String courpusSet = corpusSets.get(fileName).getContent();
			
			String corpuses[] = courpusSet.split(" ");
			TreeSet<String> wordSet = new TreeSet<String>();
			for (int i = 0; i < corpuses.length; i++) {
				String word = corpuses[i];
				if (!word.trim().equals("") && !wordSet.contains(word)) {
					wordSet.add(word);
				}
			}
			
			Iterator<String> iterator = wordSet.iterator();
			while (iterator.hasNext()) {
				String word = iterator.next();
				if (countTable.containsKey(word)) {
					Integer count = (Integer) countTable.get(word) + 1;
					countTable.remove(word);
					countTable.put(word, count);
				} else {
					countTable.put(word, 1);
				}
			}
		}
		
		return countTable;
	}	
	
	/**
	 * Compute length score of each source file then write them to LengthScore.txt file  
	 * 
	 * @throws Exception
	 */
	public void computeLengthScore(String version) throws Exception {
		SourceFileDAO sourceFileDAO = new SourceFileDAO();
		Property property = Property.getInstance();
		
		int max = 0x80000000;
		HashMap<String, Integer> lensTable = sourceFileDAO.getTotalCorpusLengths(version);
	
		int count = 0;
		int sum = 0;
		int totalCorpusLength = 0;
		Iterator<String> lensIter = lensTable.keySet().iterator();
		while (lensIter.hasNext()) {
			totalCorpusLength = lensTable.get(lensIter.next());
			if (totalCorpusLength != 0) {
				count++;
			}
			
			if (totalCorpusLength > max) {
				max = totalCorpusLength;
			}
			
			sum += totalCorpusLength;
		}
		
		double average = (double) sum / (double) count;
		double squareDevi = 0.0D;
		
 		lensIter = lensTable.keySet().iterator();
		while (lensIter.hasNext()) {
			totalCorpusLength = lensTable.get(lensIter.next());
			if (0 != totalCorpusLength) {
				squareDevi += ((double) totalCorpusLength - average) * ((double) totalCorpusLength - average);
			}
		}
		
		double standardDevi = Math.sqrt(squareDevi / (double) count);
		double low = average - 3D * standardDevi;
		double high = average + 3D * standardDevi;
		int min = 0;
		if (low > 0.0D) {
			min = (int) low;
		}
		
		lensIter = lensTable.keySet().iterator();
		while (lensIter.hasNext()) {
			String fileName = lensIter.next();
			totalCorpusLength = lensTable.get(fileName);
			double score = 0.0D;
			double nor = getNormalizedValue(totalCorpusLength, high, min);
			if (totalCorpusLength != 0) {
				if ((double) totalCorpusLength > low && (double) totalCorpusLength < high) {
					score = getLengthScore(nor);
				} else if ((double) totalCorpusLength < low) {
					score = 0.5D;
				} else {
					score = 1.0D;
				}
			} else {
				score = 0.0D;
			}
			if (nor > 6D) {
				nor = 6D;
			}
			if (score < 0.5D) {
				score = 0.5D;
			}
			
//			System.out.printf("FileName: %s, score: %f\n", fileName, score);
			sourceFileDAO.updateLengthScore(fileName, version, score);
		}
	}

	/**
	 * Get normalized value of x from Max. to min.
	 * 
	 * @param x
	 * @param max
	 * @param min
	 * @return
	 */
	private double getNormalizedValue(int x, double max, double min) {
		return (6F * (x - min)) / (max - min);
	}

	/**
	 * Get length score of BugLocator
	 * 
	 * @param len
	 * @return
	 */
	public double getLengthScore(double len) {
		return Math.exp(len) / (1.0D + Math.exp(len));
	}
	
	/* (non-Javadoc)
	 * @see edu.skku.selab.blia.indexer.IIndexer#createIndex()
	 */
	public void createIndex(String version) throws Exception {
		Property property = Property.getInstance();
		Hashtable<String, Integer> inverseDocCountTable = getInverseDocCountTable(version);
		// set total word count
		property.setWordCount(inverseDocCountTable.size());	
		
		SourceFileDAO sourceFileDAO = new SourceFileDAO();

		// insert corpus
		String term = "";
		Iterator<String> idcTableIter = inverseDocCountTable.keySet().iterator();
		while (idcTableIter.hasNext()) {
			term = idcTableIter.next();
			sourceFileDAO.insertTerm(term);
		}
		
		HashMap<String, SourceFileCorpus> corpusMap = sourceFileDAO.getCorpusMap(version);
		
		String fileName = "";
		int totalCorpusCount = 0;
		int termCount = 0;
		int inverseDocCount = 0;
		String corpusSet = "";
		Iterator<String> corpusSetsIter = corpusMap.keySet().iterator();
		while (corpusSetsIter.hasNext()) {
			fileName = corpusSetsIter.next();
			corpusSet = corpusMap.get(fileName).getContent();
			
//			System.out.printf("File Name: %s\n", fileName);
//			System.out.printf("CorpusSet: %s\n", corpusSet);

			// get term count
			String termArray[] = corpusSet.split(" ");
			totalCorpusCount = 0;
			Hashtable<String, Integer> termTable = new Hashtable<String, Integer>();
			for (int i = 0; i < termArray.length; i++) {
				term = termArray[i];
				if (!term.trim().equals("")) {
					totalCorpusCount++;
					if (termTable.containsKey(term)) {
						Integer count = (Integer) termTable.get(term);
						count = Integer.valueOf(count.intValue() + 1);
						termTable.remove(term);
						termTable.put(term, count);
					} else {
						termTable.put(term, Integer.valueOf(1));
					}
				}
			}
			
			sourceFileDAO.updateTotalCoupusCount(fileName, version, totalCorpusCount);

			Iterator<String> termTableIter = termTable.keySet().iterator();
			while (termTableIter.hasNext()) {
				term = termTableIter.next();
				termCount = termTable.get(term);
				inverseDocCount = inverseDocCountTable.get(term).intValue();
				AnalysisValue termWeight = new AnalysisValue(fileName, version, term, termCount, inverseDocCount);
				sourceFileDAO.insertTermWeight(termWeight);		
			}
		}
	}
    
    public static HashSet<String> CorpusToSet(String corpus) {
    	HashSet<String> termSet = new HashSet<String>();
    	
    	String[] stringArray = corpus.split(" ");
   		for (int i = 0; i < stringArray.length; i++) {
   			termSet.add(stringArray[i]);
    	}
    	
    	return termSet;
    }
    
    private class WorkerThread implements Runnable {
    	private String fileName;
    	private String version;
    	
    	
        public WorkerThread(String fileName, String version){
            this.fileName = fileName;
            this.version = version;
        }
     
        @Override
        public void run() {
			// Compute similarity between Bug report & source files
        	
        	try {
        		insertDataToDb();
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        }
        
        private void insertDataToDb() throws Exception {
        	SourceFileDAO sourceFileDAO = new SourceFileDAO();
        	
//			if (fileName.equalsIgnoreCase("org.eclipse.swt.internal.win32.NMCUSTOMDRAW.java")) {
				Integer totalTermCount = totalCorpusLengths.get(fileName);
				
				HashMap<String, AnalysisValue> sourceFileTermMap = sourceFileDAO.getTermMap(fileName, version);
				if (sourceFileTermMap == null) {
					// debug code
					System.out.printf("[SourceFileVectorCreator.create()] The file name that has no valid terms: %s\n", fileName);
					return;
				}

				double corpusNorm = 0.0D;
				double classCorpusNorm = 0.0D;
				double methodCorpusNorm = 0.0D;
				double variableNorm = 0.0D;
				double commentNorm = 0.0D;
				
				SourceFileCorpus sourceFileCorpus = sourceFileCorpusMap.get(fileName);
				HashSet<String> classTermSet = CorpusToSet(sourceFileCorpus.getClassPart());
				HashSet<String> methodTermSet = CorpusToSet(sourceFileCorpus.getMethodPart());
				HashSet<String> variableTermSet = CorpusToSet(sourceFileCorpus.getVariablePart());
				HashSet<String> commentTermSet = CorpusToSet(sourceFileCorpus.getCommentPart());				
				
				Iterator<String> sourceFileTermIter = sourceFileTermMap.keySet().iterator();
				while (sourceFileTermIter.hasNext()) {
					String term = sourceFileTermIter.next();
					AnalysisValue termWeight = sourceFileTermMap.get(term);
					double tf = getTfValue(termWeight.getTermCount(), totalTermCount.intValue());
					double idf = getIdfValue(termWeight.getInvDocCount(), fileCount);
					double termWeightValue = (tf * idf);
					double termWeightValueSquare = termWeightValue * termWeightValue;
					
//					System.out.printf("term: %s, termCount: %d, documentCount: %d, tf: %f, idf: %f, termWeight: %f\n",
//							term, termWeight.getTermCount(), termWeight.getInvDocCount(), tf, idf, termWeightValue);
					corpusNorm += termWeightValueSquare;
					
					if (classTermSet.contains(term)) {
						classCorpusNorm += termWeightValueSquare;
					}

					if (methodTermSet.contains(term)) {
						methodCorpusNorm += termWeightValueSquare;
					}

					if (variableTermSet.contains(term)) {
						variableNorm += termWeightValueSquare;
					}

					if (commentTermSet.contains(term)) {
						commentNorm += termWeightValueSquare;
					}

					termWeight.setTf(tf);
					termWeight.setIdf(idf);
					sourceFileDAO.updateTermWeight(termWeight);
				}
				corpusNorm = Math.sqrt(corpusNorm);
				classCorpusNorm = Math.sqrt(classCorpusNorm);
				methodCorpusNorm = Math.sqrt(methodCorpusNorm);
				variableNorm = Math.sqrt(variableNorm);
				commentNorm = Math.sqrt(commentNorm);
//				System.out.printf(">>>> corpusNorm: %f, classCorpusNorm: %f, methodCorpusNorm: %f, variableNorm: %f, commentNorm: %f\n",
//						corpusNorm, classCorpusNorm, methodCorpusNorm, variableNorm, variableNorm);
				
				sourceFileDAO.updateNormValues(fileName, version, corpusNorm, classCorpusNorm, methodCorpusNorm, variableNorm, commentNorm);
        }
    }


	/* (non-Javadoc)
	 * @see edu.skku.selab.blia.indexer.IVectorCreator#create()
	 */
	public void create(String version) throws Exception {
		SourceFileDAO sourceFileDAO = new SourceFileDAO();
		totalCorpusLengths = sourceFileDAO.getTotalCorpusLengths(version);
		sourceFileCorpusMap = sourceFileDAO.getCorpusMap(version);
		fileCount = sourceFileDAO.getSourceFileCount(version);
		
		// Calculate vector
		Iterator<String> fileNameIter = totalCorpusLengths.keySet().iterator();
		ExecutorService executor = Executors.newFixedThreadPool(Property.THREAD_COUNT);
		while (fileNameIter.hasNext()) {
			String fileName = fileNameIter.next();
			Runnable worker = new WorkerThread(fileName, version);
			executor.execute(worker);
		}
		
		executor.shutdown();
		while (!executor.isTerminated()) {
		}
	}
	
	private float getTfValue(int freq, int totalTermCount) {
		return (float) Math.log(freq) + 1.0F;
	}

	private float getIdfValue(double docCount, double totalCount) {
		return (float) Math.log(totalCount / docCount);
	}

}
