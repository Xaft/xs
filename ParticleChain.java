package prat;
import ij.*;
public class ParticleChain{
	public Particle[] element;
	public int boundZ;
	public int boundD;
	public int sniffCnt;
	public int[] sniffPos;
	public double[] sniffScore; 
	private String comment;
	public boolean classified;	
	public double physLength;
	public double linearity;
	/*public ParticleChain() {

		element = new Particle[1];
		element[0]= new Particle();
		boundZ=0;
		boundD=0;
		sniffCnt=0;
		sniffPos=new int[9];
		sniffScore=new double[9];
		comment="";
		
	}*/
	public ParticleChain(int Z){
		element = new Particle[0];
		boundZ=Z;
		boundD=0;
		sniffCnt=0;
		sniffPos=new int[9];
		sniffScore=new double[9];
		comment="";
		classified=false;
		physLength=0.0;
		linearity=1.0;
	}
	public ParticleChain(double cX, double cY, int a, int X, int Y, int W, int H, int Z){
		element = new Particle[1];
		element[0]=new Particle(cX,cY,a,X,Y,W,H);
		boundZ=Z;
		boundD=1;
		sniffCnt=0;
		sniffPos=new int[9];
		sniffScore=new double[9];
		comment="";
		classified=false;
		physLength=0.0;
		linearity=1.0;
		}
	public ParticleChain(Particle Pr, int Z){
		element = new Particle[1];
		element[0]=Pr;
		boundZ=Z;
		boundD=1;
		sniffCnt=0;
		sniffPos=new int[9];
		sniffScore=new double[9];
		comment="";
		classified=false;
		physLength=0.0;
		linearity=1.0;
		}
	
	public ParticleChain(ParticleChain ctM){
		element=new Particle[ctM.boundD];
		System.arraycopy(element,0,ctM.element,0,ctM.boundD);
		boundD=ctM.boundD;
		boundZ=ctM.boundZ;
		sniffCnt=0;
		sniffPos=new int[9];
		sniffScore=new double[9];
		comment="";
		classified=false;
		physLength=ctM.getLength();
		linearity=ctM.linearity;
	}
	public void calculateLength(){
		physLength=0.0;
		for (int q=0;q<boundD-1;q++) physLength+=Math.sqrt(Math.pow(element[q+1].centerX-element[q].centerX,2)+Math.pow(element[q+1].centerY-element[q].centerY,2));										
	}
	public double getLength(){
		return physLength;
	}
	public void increaseLength(double a){
		physLength+=a;
	}
	public void addComment(String cmnt){
		this.comment=cmnt;
	}
	public String getComment(){
		return this.comment;
	}
	public void linkParticle(Particle Pr){
		boundD++;
		Particle[] tmp= new Particle[boundD];
		System.arraycopy(element, 0, tmp, 0, boundD-1);
		element=tmp;
		element[boundD-1]=Pr;
		if (boundD>1) {
			physLength+=Math.sqrt(Math.pow(element[boundD-1].centerX-element[boundD-2].centerX,2)+Math.pow(element[boundD-1].centerY-element[boundD-2].centerY,2));	
			linearity=Math.sqrt(Math.pow(element[boundD-1].centerX-element[boundD-2].centerX,2)+Math.pow(element[0].centerY-element[0].centerY,2))/physLength;
		}
	}

	public boolean shrinkChain(int Z, int D){
		if ((Z<boundZ)||(Z>boundZ+boundD)||(D>boundD)||(D<1)) return false;
		else {
			Particle[] tmp=new Particle[D];
			System.arraycopy(this.element, Z-boundZ, tmp, 0, D);
			element=tmp;
			this.boundZ=Z;
			this.boundD=D;
			return true;
		}
	}
	
	public boolean mergeChain(ParticleChain ctM){
		//IJ.log("merge");
		if (ctM.boundZ<this.boundZ){
			if (ctM.boundZ+ctM.boundD!=this.boundZ) return false;
			else{
				Particle tmp[]=new Particle[ctM.boundD+this.boundD];
				System.arraycopy(ctM.element, 0, tmp, 0, ctM.boundD);
				System.arraycopy(this.element, 0, tmp, ctM.boundD, this.boundD);
				//IJ.log("mergeDone");
				this.element=tmp;
				this.boundZ=ctM.boundZ;
				this.boundD=this.boundD+ctM.boundD;
				return true;
			}
		}
		else if (ctM.boundZ>this.boundZ){
			IJ.log(ctM.boundZ+"-"+this.boundZ+"-"+this.boundD);
			if (this.boundZ+this.boundD!=ctM.boundZ) return false;
			else{
				Particle tmp[]=new Particle[ctM.boundD+this.boundD];
				System.arraycopy(this.element, 0, tmp, 0, this.boundD);
				System.arraycopy(ctM.element, 0, tmp, this.boundD, ctM.boundD);
				//IJ.log("mergeDone");
				this.element=tmp;
				this.boundD=this.boundD+ctM.boundD;
				return true;
			}
		}
		else return false;
	}
	public void removeLast(){
		this.element[this.boundD-1]=null;
		boundD--;
	}
	public Particle last(){
		return this.element[this.boundD-1];
	}
	public Particle first(){
		return this.element[0];
	}
	public void addSniff(int pos, double score){
		this.sniffPos[sniffCnt]=pos;
		this.sniffScore[sniffCnt]=score;
		if (++sniffCnt>=sniffPos.length) {
			int[] newData = new int[sniffCnt*2];
			System.arraycopy(sniffPos, 0, newData, 0, sniffPos.length);
			sniffPos = newData;
			double[] newdData = new double[sniffCnt*2];
			System.arraycopy(sniffScore, 0, newdData, 0, sniffScore.length);
			sniffScore = newdData;
		}
	}
	public void arrangeSniffs(){
		for (int i=1;i<sniffCnt;i++){
			double tvalue=sniffScore[i];
			int value=sniffPos[i];
			int j=i-1;
			boolean done=false;
			do{
				if (sniffScore[j]>tvalue){
					sniffScore[j+1]=sniffScore[j];
					sniffPos[j+1]=sniffPos[j];
					j--;
					if (j<0) done=true;
				}
				else done=true;
			}while(!done);
			sniffPos[j+1]=value;
			sniffScore[j+1]=tvalue;
		}
	}
}		