package org.bgi.flexlab.gaea.data.structure.reads;

import org.bgi.flexlab.gaea.data.structure.reads.report.ArrayListLongWrap;
import org.bgi.flexlab.gaea.data.structure.reads.report.FastqQualityControlReport;
import org.bgi.flexlab.gaea.tools.mapreduce.fastqqualitycontrol.FastqQualityControlOptions;
import org.bgi.flexlab.gaea.util.BaseUtils;

public class ReadBasicStatic {
	private short[] basicCounts;
	private short baseNumber;
	private short lowQualityBaseNumber;
	private FastqQualityControlOptions option;
	private String sampleID = null;
	private boolean sampleIDException = false;

	private byte readCount;

	private boolean[] lowQualityRead = new boolean[2];
	private boolean[] tooManyNCounter = new boolean[2];
	private boolean[] hasAdaptor = new boolean[2];
	
	private ArrayListLongWrap[] baseByPosition;
	private final int SIZE = FastqQualityControlReport.BASE_STATIC_COUNT/2;

	public ReadBasicStatic(FastqQualityControlOptions option) {
		this.basicCounts = new short[7];
		this.lowQualityBaseNumber = 0;
		this.baseNumber = 0;
		this.option = option;
		this.baseByPosition = new ArrayListLongWrap[SIZE];
		
		for(int i=0;i<SIZE;i++)
			baseByPosition[i] = new ArrayListLongWrap();
	}
	
	private void setSampleID(String sID){
		if(sampleID == null)
			sampleID = sID;
		else if(!sampleID.equals(sID)){
			sampleIDException = true;
		}
	}

	public void countBase(ReadInformationWithSampleID read, int flag) {
		this.readCount++;
		setSampleID(read.getSampleID());
		byte[] basic = read.getReadsSequence().getBytes();
		byte[] quality = read.getQualityString().getBytes();

		int length = read.getReadLength();

		this.baseNumber += length;

		short lowQual = 0;
		short nCount = 0;

		for (int i = 0; i < length; i++) {
			int posQuality = quality[i] - option.getQuality();

			if (posQuality >= 20) {
				basicCounts[5]++;
				baseByPosition[5].add(i, 1);
			}
			if (posQuality >= 30) {
				basicCounts[6]++;
				baseByPosition[6].add(i, 1);
			}
			if (posQuality < option.getLowQuality()) {
				lowQual++;
			}
			int baseIndex = BaseUtils.getBinaryBase(basic[i]);
			basicCounts[baseIndex]++;
			baseByPosition[baseIndex].add(i, 1);

			if (baseIndex == 4)
				nCount++;
		}
		this.lowQualityBaseNumber += lowQual;
		
		setBadType(nCount,lowQual,length,flag);
	}
	
	private void setBadType(int nCount,int lowQual,int length,int flag){
		if(hasAdaptor[flag])
			return;

		if (((double) ((double) nCount / length)) >= option.getNRate()) {
			tooManyNCounter[flag] = true;
			return;
		}
		if (((double) lowQual / length) >= option.getQualityRate()) {
			lowQualityRead[flag] = true;
			return;
		}
	}

	public void addReads(ReadBasicStatic basicStatic) {
		for (int i = 0; i < basicCounts.length; i++) {
			basicCounts[i] += basicStatic.getBasicBaseCount(i);
		}

		lowQualityBaseNumber += basicStatic.getLowQualityNumber();
	}

	public short getBasicBaseCount(int index) {
		return basicCounts[index];
	}

	public short getLowQualityNumber() {
		return this.lowQualityBaseNumber;
	}

	public void setAdaptor(int readFlag) {
		hasAdaptor[readFlag - 1] = true;
	}

	public int getReadCount() {
		return this.readCount;
	}

	public int getProblemReadsNum() {
		int problemReadsNum = 0;

		for (int i = 0; i < 2; i++) {
			if (hasAdaptor[i] || tooManyNCounter[i] || lowQualityRead[i])
				problemReadsNum++;
		}
		return problemReadsNum;
	}

	public int getBaseNumber() {
		return this.baseNumber;
	}
	
	public int getCounter(boolean[] array){
		int cnt = 0;
		for(int i = 0 ; i < 2 ;i++){
			if(array[i])
				cnt++;
		}
		return cnt;
	}
	
	public int getAdaptorCount(){
		return getCounter(hasAdaptor);
	}
	
	public int getTooManyNCounter(){
		return getCounter(tooManyNCounter);
	}
	
	public int getLowQualityCounter(){
		return getCounter(lowQualityRead);
	}
	
	public boolean isSampleIDException(){
		return sampleIDException;
	}
	
	public String getSampleID(){
		return this.sampleID;
	}
	
	public ArrayListLongWrap getPositionInfo(int index){
		return this.baseByPosition[index];
	}
}
