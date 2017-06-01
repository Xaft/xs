package prat;

import java.lang.*;
import java.lang.Math;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.filter.GaussianBlur;
import java.awt.geom.Ellipse2D;

public final class processAFrame {
	private static int maxSteps=10000;
	private int W;
	private int H; 
	private int max;
	private int bckgF;
	private double part_R;
	private int imgType;
	private double dynamics, sensitivity, dtlContrast;
	private static boolean invert=false;
	private ImagePlus DoG0;
	private double kernelSize;
	private int filterType;
	private int centeringType;
	public final static int CoM=1, BRIGHTEST=2, BRIGHTEST_FILTERED=3, GAUSSIAN1D=4;
	public processAFrame(int _imgType, int w, int h, double bckgf, double dyn, double sens, double contr, int fT){
		this.W=w;
		this.H=h;
		this.part_R=bckgf;
		this.bckgF=(int)(bckgf*10);
		this.dynamics=dyn;
		this.sensitivity=sens;
		this.dtlContrast=contr;
		this.max=w*h;
		this.imgType=_imgType;
		this.DoG0=NewImage.createFloatImage("0", W, H,1, NewImage.FILL_BLACK);
		this.kernelSize=-1;
		this.filterType=fT;
		this.centeringType=2;
	}
	public processAFrame(int _imgType, int w, int h, double bckgf, double dyn, double sens, double contr, int fT, int cT){
		this( _imgType,  w,  h,  bckgf,  dyn,  sens,  contr, fT);
		this.centeringType=cT;
	}
	public void reFeedParams(double bckgf, double dyn, double sens, double contr, int fT){
		this.part_R=bckgf;
		this.bckgF=(int)(bckgf*10);
		this.dynamics=dyn;
		this.sensitivity=sens;
		this.dtlContrast=contr;
		this.filterType=fT;
		this.centeringType=1;
	}
	public void reFeedParams(double bckgf, double dyn, double sens, double contr, int fT, int cT){
		reFeedParams(bckgf,  dyn, sens, contr, fT);
		this.centeringType=cT;
	}
	public void reset(){
		this.kernelSize=-1;
	}
	public void close(){
		DoG0.close();
	}
	public int[] getBckgfilteredPxs(ImageProcessor ip){
		float[] bckg=new float[max];
		int[] finalWater2 = getPxels(ip);
		if (bckgF!=0) bckg=getBackground(finalWater2, bckgF, W, H,max, 1);

		float ftmp;
		for (int i=0;i<max;i++) finalWater2[i]=(ftmp=(finalWater2[i]-bckg[i]))<0?0:(int)ftmp;
		return finalWater2;
	}
	public float[][] getBckgfiltered(ImageProcessor ip){
		float[][] ret=new float[2][max];
		int[] finalWater2 = getPxels(ip);
		if (bckgF!=0) ret[0]=getBackground(finalWater2, bckgF, W, H,max, 1);
		float ftmp;
		for (int i=0;i<max;i++) ret[1][i]=(ftmp=(finalWater2[i]-ret[0][i]))<0?0:ftmp;
		return ret;
	}
	public Particle[] getAllParticlesonaFrame(ImageProcessor ip){
		//IJ.log("processAFrame");
		int[] waterlevel=new int[max];
		int[] waterFlow = new int[max];
		//double[] mean=new double[max];
		float[] bckg=new float[max];
		int[] finalWater2 = getPxels(ip);
		//IJ.log("processAFrame: fW21");
		if (bckgF!=0) bckg=getBackground(finalWater2, bckgF, W, H,max, 1);
		//else bckg=getBackground(finalWater2, 50, W, H,max, 1);
		
		//IJ.log("processAFrame: bckg");
		/*if (imgType==16) {
			
			IJ.log("16bit_bckg");
			drawArrayFloat16(bckg,W,H,this.bckgF+"");
		}
		else drawArrayFloat(bckg,W,H,this.bckgF+"");
		*/
		int[] fpxs=new int[max];//sqrtFilter(finalWater2,1,max);
		float ftmp;
		if (bckgF!=0) for (int i=0;i<max;i++) finalWater2[i]=(ftmp=(finalWater2[i]-bckg[i]))<0?0:(int)ftmp;
		switch (this.filterType){
			case 1:
				ImageProcessor D0p=DoG0.getProcessor();
				float[] DoG0px=(float[])D0p.getPixels();
				if (this.kernelSize!=this.part_R){
					for (int i=0;i<max;i++) fpxs[i]=finalWater2[i];
					expandRange(fpxs);
					ImagePlus DoG1=NewImage.createFloatImage("1", W, H,1, NewImage.FILL_BLACK);
					ImageProcessor D1p=DoG1.getProcessor();
					float[] DoG1px=(float[])D1p.getPixels();
					for (int i=0;i<max;i++) {
						DoG0px[i]=(float)fpxs[i];
						DoG1px[i]=(float)fpxs[i];
					}
					GaussianBlur gB=new GaussianBlur();
					gB.blurGaussian(D1p, this.part_R+1, this.part_R+1, 0.002);
					gB.blurGaussian(D0p, this.part_R, this.part_R, 0.002);
					int itmp=0;
					for (int i=0;i<max;i++) DoG0px[i]= (DoG0px[i]-DoG1px[i])>0?(DoG0px[i]-DoG1px[i]):0;
					DoG1.close();
					this.kernelSize=this.part_R;
				}
				for (int i=0;i<max;i++){
					fpxs[i]=(int)DoG0px[i];
				}
				break;
			case 2: 
				fpxs=sqrtFilter(finalWater2,1,max);
				break;
			default: for (int i=0;i<max;i++) fpxs[i]=finalWater2[i];
				break;
		}

		for (int i=0;i<max;i++) if (fpxs[i]!=0) waterlevel[i]=1;
		maketheWaterFlow(fpxs, waterlevel, waterFlow, W, H);
		int est=(int)(max);
		filteredParticle[] initialParticles=walkAgainstWater(finalWater2,bckg, waterlevel, waterFlow, fpxs,  W,  max, est);
		filteredParticle[] Particles = mergeParticles(initialParticles, waterFlow,W,H,sensitivity);
		for (int i=0;i<Particles.length;i++){
			Particles[i].trimPercentofMax((float)dtlContrast);
		}
		int retc=0;
		filteredParticle[] retParticles =new filteredParticle[Particles.length];
		int cnt=0;
		//ResultsTable res=ResultsTable.getResultsTable();
		for (int i=0;i<Particles.length;i++){
			Particles[i].getWeight();
			if (Particles[i].weight>0){
				Particles[i].getEdges(false);
				if (Particles[i].getShape(false)) {
					Particles[i].bckg=bckg[Particles[i].brightestX+Particles[i].brightestY*W];
					Particles[i].bckgs[0]=Particles[i].bckg;
					retParticles[cnt]=Particles[i];
					switch (centeringType){
						case 1: retParticles[cnt].getCentered();break;
						case 2: retParticles[cnt].getCenteredByBrightest();break;
						case 3: retParticles[cnt].getCenteredByFiltered((int)this.part_R);break;
						case 4: retParticles[cnt].getCenteredBy1DGaussian();break;
						default: break;
					}
					if (retParticles[cnt].R<=3) retParticles[cnt].shpe=new Ellipse2D.Double(retParticles[cnt].centerX-retParticles[cnt].R, retParticles[cnt].centerY-retParticles[cnt].R,retParticles[cnt].R*2,retParticles[cnt].R*2);
					cnt++;
					}
				
			}
			else Particles[i]=null;
		}
		//res.show("Results");
		initialParticles=null;
		Particles=null;
		return (Particle[]) retParticles;
	}
	public int[] getPxels(ImageProcessor ip){
		int [] ret=new int[max];
		if (imgType==8) {
			byte[] pxs=(byte[]) ip.getPixels();
			for (int i=0;i<max;i++)ret[i]=pxs[i]&0xff;
		}
		else if (imgType==16) {
			short[] pxs=(short[]) ip.getPixels();
			for (int i=0;i<max;i++)ret[i]=pxs[i]&0xffff;
		}
		int invType=2<<imgType-1;
		if (invert) {
			for (int i=0;i<max;i++) ret[i]=invType-ret[i];
		}
		return ret;
	}
	void expandRange(int[] ipxs){
		int bD=2<<(imgType-1);
		int min=bD, max=0;
		int i=0;
		for (i=0;i<ipxs.length;i++) {
			if (ipxs[i]<min) min=ipxs[i];
			if (ipxs[i]>max) max=ipxs[i];
		}
		float scaleUp=(float)bD/(max-min);
		bD--;
		//IJ.log("min:"+min+", max:"+max+", scale:"+scaleUp+", range:"+bD);
		for (i=0;i<ipxs.length;i++) {
			ipxs[i]=(int)((float)(ipxs[i]-min)*scaleUp);
		}
	}
	int [] sqrtFilter(int[] ipxs, int windowSize, int max){
		int i,j,k,l,index,index2,sum;
		int[] ret=new int[max];
		int padded_W=(W+2*windowSize+1);
		int padded_H=(H+2*windowSize+1);
		int padded_max=padded_W*padded_H;
		int[] padded_pxs=new int[padded_max];
		int N=(int)Math.pow((windowSize*2+1),2);
		for (i=0;i<windowSize;i++){
			for (j=0;j<windowSize;j++){
				padded_pxs[i*padded_W+j]=ipxs[0];
				padded_pxs[(padded_H-i-2)*padded_W+j]=ipxs[max-W+1];
				padded_pxs[i*padded_W+W+j+windowSize]=ipxs[W];
				padded_pxs[(padded_H-i-2)*padded_W+W+j+windowSize]=ipxs[max-1];
			}
			for (j=0;j<W;j++){
				padded_pxs[i*padded_W+j+windowSize]=ipxs[j];
				padded_pxs[(padded_H-i-2)*padded_W+j+windowSize]=ipxs[(H-1)*W+j];
			}
		}
		for (i=0;i<H;i++){
			for (j=0;j<W;j++){
				padded_pxs[(i+windowSize)*padded_W+j+windowSize]=ipxs[i*W+j];
			}
			for (j=0;j<windowSize;j++){
				padded_pxs[(i+windowSize)*padded_W+j]=ipxs[i*W];
				padded_pxs[(i+windowSize)*padded_W+W+j+windowSize]=ipxs[(i+1)*W-1];
			}
		}
		for (i=windowSize;i<H+windowSize;i++){
			for (j=windowSize;j<W+windowSize;j++){
				index=i*padded_W+j;
				index2=(i-1)*W+j-1;
				sum=0;
				for (k=-windowSize;k<=windowSize;k++){
					for (l=-windowSize;l<=windowSize;l++){
						sum+=padded_pxs[index+k*padded_W+l];
					}
				}
				ret[index2]=(int)(Math.sqrt((sum/N)*padded_pxs[index]));
			}
		}
	return ret;
	}
	
	int getLocalMaxima(Particle P, int[] tmPxs){
		int i,j,k,offset, max, tIndex, mIndex, sum, cnt;
		int[] indexA={0,1,P.boundW,P.boundW+1};
		for (i=0;i<P.boundH*P.boundW;i++) tmPxs[i]=P.pxs[i];
		for (i=0;i<P.boundH-1;i++){
			for (j=0;j<P.boundW-1;j++){
				offset=i*P.boundW+j;
				max=tmPxs[offset];
				mIndex=offset;
				for (k=1;k<4;k++){
					tIndex=offset+indexA[k];
					if (tmPxs[tIndex]>=max) {max=tmPxs[tIndex]; mIndex=tIndex;}
					}
				for (k=0;k<4;k++) tmPxs[offset+indexA[k]]=0;
				tmPxs[mIndex]=max;
			}
		}
		sum=0;
		cnt=0;
		for (i=0;i<P.boundH*P.boundW;i++) {
			if (tmPxs[i]>0) {sum+=tmPxs[i];cnt++;}
		}
		
		if (cnt>1){
			double split=0.8*(double)(sum)/cnt;
			for (j=0;j<P.boundH*P.boundW;j++) if (P.pxs[j]<split) tmPxs[j]=0;
				else tmPxs[j]=P.pxs[j];
		}
		return cnt;
	}
	void maketheWaterFlow(int[] finalWater2, int[]waterlevel, int[] waterFlow, int W, int H){
		int max=W*H;
		int waterareaC=-1;
		int[] eqSteps={0,1,W-1,W,W+1};

		while(waterareaC<0){
			waterareaC=0;
			for (int i=1;i<H-1;i++){
				for (int j=1;j<W-1;j++){
					int offset=i*W+j;
					if ((waterlevel[offset]>0)){
						boolean flow=false;
						int maxdiff=0;
						int[] maxdiffpos={20,20};
						for (int k=-1;k<2;k++){
							for (int l=-1;l<2;l++){
								int index=offset+k*W+l;
								if ((index>=0)&&(index<max)&&(index!=offset)&&(finalWater2[index]>(finalWater2[offset]))){ 
									waterlevel[index]+=waterlevel[offset];
									waterlevel[offset]=0;
									waterFlow[offset]=-(k*3+l);
									waterareaC--;
									flow=true;
									break;
								}
								if (flow) break;
							}
						}
						if ((!flow)&&(finalWater2[offset]>0)&&waterlevel[offset]>=1) {
							for (int k=1;k<5;k++){
								int index=offset+eqSteps[k];
								if ((index>=0)&&(index<max)&&(finalWater2[index]==finalWater2[offset])) {
									waterlevel[index]+=waterlevel[offset];
									waterlevel[offset]=0;
									waterFlow[offset]=-(k);
									waterareaC--;
									flow=true;
									break;	
								}
							}
						}
				
					}
				}
			}
		}
		
		int[] waterFlowT=new int[max];
		for (int i=0;i<max;i++) waterFlowT[i]=(waterFlow[i]+4)*25; 
		
		return;
	}
	float[] getBackground(int[]pxs, int meansize, int W, int H, int max, float factor){
		int padded_max=(W+2*meansize+1)*(H+2*meansize+1);
		int padded_W=(W+2*meansize+1);
		int padded_H=(H+2*meansize+1);
		int[] padded_pxs=new int[padded_max];
		int i,j,k,l,nmean,index,offset;
		int p0=getPercentileValue(pxs,0.1);
		int p1=getPercentileValue(pxs,0.9);
		
		for (i=0;i<meansize;i++){
			for (j=0;j<meansize;j++){
				padded_pxs[i*padded_W+j]=pxs[(meansize-i-1)*W+meansize-j-1];
				padded_pxs[(padded_H-i-2)*padded_W+j]=pxs[(H-meansize+i-1)*W+meansize-j-1];
				padded_pxs[i*padded_W+W+j+meansize]=pxs[(meansize-i-1)*W+W-j];
				padded_pxs[(padded_H-i-2)*padded_W+W+j+meansize]=pxs[(H-meansize+i-1)*W+W-j];
				}
			for (j=0;j<W;j++){
				padded_pxs[i*padded_W+j+meansize]=pxs[(meansize-i-1)*W+j];
				padded_pxs[(padded_H-i-2)*padded_W+j+meansize]=pxs[(H-meansize+i-1)*W+j];
			}
		}
		
		for (i=0;i<H;i++){
			for (j=0;j<W;j++){
				padded_pxs[(i+meansize)*padded_W+j+meansize]=pxs[i*W+j];
			}
			for (j=0;j<meansize;j++){
				padded_pxs[(i+meansize)*padded_W+j]=pxs[i*W+meansize-1-j];
				padded_pxs[(i+meansize)*padded_W+W+j+meansize]=pxs[i*W+W-1-j];
			}
		}
		for (i=1;i<=padded_H;i++) padded_pxs[i*padded_W-1]=padded_pxs[i*padded_W-2];
		for (i=0;i<padded_W;i++) padded_pxs[(padded_H-1)*padded_W+i]=padded_pxs[(padded_H-2)*padded_W+i];
		
        float[] tmean={0.0f,0.0f,0.0f,0.0f,0.0f};
		int[] n={0,0,0,0,0};
		float[] mean=new float[max];
		
		for (i=0;i<H;i++){
			tmean[2]=0f;
			n[2]=0;
			for (k=0;k<2*meansize+1;k++){
				for (l=0;l<2*meansize+1;l++){
					if (padded_pxs[(i+k)*padded_W+l]>=p0&&padded_pxs[(i+k)*padded_W+l]<=p1) {
						tmean[2]+=padded_pxs[(i+k)*padded_W+l];
						n[2]++;
					}
				}
			}
			for (j=0;j<W;j++){
				tmean[2]+=tmean[3]-tmean[1];
				n[2]+=n[3]-n[1];
				if (n[2]>=meansize) mean[i*W+j]=factor*tmean[2]/n[2];
				else if (j>0) mean[i*W+j]=mean[i*W+j-1];
				else mean[i*W+j]=0;
				tmean[1]=0f;tmean[3]=0f;
				n[1]=0;n[3]=0;
				for (k=0;k<2*meansize+1;k++){
					if (padded_pxs[(i+k)*padded_W+j]>=p0&&padded_pxs[(i+k)*padded_W+j]<=p1) {
						tmean[1]+=padded_pxs[(i+k)*padded_W+j];
						n[1]++;
					}
					if (padded_pxs[(i+k)*padded_W+j+2*meansize+1]>=p0&&padded_pxs[(i+k)*padded_W+j+2*meansize+1]<=p1) {
						tmean[3]+=padded_pxs[(i+k)*padded_W+j+2*meansize+1];
						n[3]++;
					}
				}
			}
		}
		
		
		
		return mean;
	}
	filteredParticle[] mergeParticles(filteredParticle[]iP, int[] waterFlow,int W,int H, double factor){
		filteredParticle[] ret= new filteredParticle[iP.length];
		//IJ.log(factor+"f");
		int retc=0;
		int[] tmOut=new int[W*H];
		//IJ.write(ret.length+"");
		for (int i=0;i<H;i++){
			for (int j=0;j<W;j++){
				int index=i*W+j;
				if (waterFlow[index]>9){
					int[] tP = new int[iP.length+1];
					tP[0]=0;
					//IJ.write("mergeParticles");
					try{
						groupParticles(iP, tP, waterFlow, W, index, W*H,0);
						}
					catch (Throwable t){
						IJ.log(t.getLocalizedMessage());
					}
					//if (tP[0]==0) IJ.write("0"+index);
					
					if (tP[0]==1){
						
						ret[retc]=iP[tP[1]];
						retc++;
						/*Particle[] tmP=new Particle[retc+1];
						System.arraycopy(ret, 0, tmP, 0, ret.length);
						ret=tmP;
						IJ.write("1-"+retc+"*"+tP[0]+"-"+tP[1]);*/
						
						}
					else if (tP[0]>1){
						//IJ.write(tP[0]+"");
						int pairwise1=1, pairwise2=0;
						
						while (pairwise1<tP[0]){
					      
							pairwise2=pairwise1+1;
							while(pairwise2<=tP[0]){
							  if (tP[pairwise2]!=tP[pairwise1]){
								int[] iCoords=isIntersect(iP[tP[pairwise1]],iP[tP[pairwise2]]);
								/*IJ.write("X1:"+iP[tP[pairwise1]].boundX+" Y1:"+iP[tP[pairwise1]].boundY+" W1:"+iP[tP[pairwise1]].boundW+"H1:"+iP[tP[pairwise1]].boundH);
								IJ.write("X2:"+iP[tP[pairwise2]].boundX+" Y2:"+iP[tP[pairwise2]].boundY+" W2:"+iP[tP[pairwise2]].boundW+"H2:"+iP[tP[pairwise2]].boundH);
								IJ.write("Borders:"+iCoords[0]+","+iCoords[1]+" - "+iCoords[2]+","+iCoords[3]);*/
								if (iCoords[0]!=-1){
									boolean[] P1=new boolean[iCoords[2]*iCoords[3]];
									int min=(iP[tP[pairwise1]].brightest>iP[tP[pairwise2]].brightest)?iP[tP[pairwise2]].brightest:iP[tP[pairwise1]].brightest;
									min=(int)((double)min*factor);
									//IJ.write("min:"+min+"");
									int Xoffset=iP[tP[pairwise1]].boundX-iCoords[0];
									int Yoffset=iP[tP[pairwise1]].boundY-iCoords[1];
									for (int k=0;k<iP[tP[pairwise1]].boundH;k++){
										for (int l=0;l<iP[tP[pairwise1]].boundW;l++){
											if (iP[tP[pairwise1]].pxs[k*iP[tP[pairwise1]].boundW+l]>=min) P1[(k+Yoffset)*iCoords[2]+l+Xoffset]=true;
										}
									}	
									Xoffset=iP[tP[pairwise2]].boundX-iCoords[0];
									Yoffset=iP[tP[pairwise2]].boundY-iCoords[1];
									boolean merged=false;
									for (int k=0;k<iP[tP[pairwise2]].boundH;k++){
										for (int l=0;l<iP[tP[pairwise2]].boundW;l++){
											if ((iP[tP[pairwise2]].pxs[k*iP[tP[pairwise2]].boundW+l]>=min)&&(chkNghbr(P1,(k+Yoffset)*iCoords[2]+l+Xoffset,iCoords[2],iCoords[3]))) {
												iP[tP[pairwise1]].swallow(iP[tP[pairwise2]], iCoords);
												
												for (int m=0;m<iP[tP[pairwise1]].boundH;m++){
													for (int n=0;n<iP[tP[pairwise1]].boundW;n++){
														if (iP[tP[pairwise1]].pxs[m*iP[tP[pairwise1]].boundW+n]!=0) tmOut[(m+iP[tP[pairwise1]].boundY)*W+n+iP[tP[pairwise1]].boundX]=iP[tP[pairwise1]].pxs[m*iP[tP[pairwise1]].boundW+n];
													}
												}
												
												tP[pairwise2]=tP[pairwise1];
												pairwise2=pairwise1;
												merged=true;
												break;
											}
										
										}
									if (merged) break;
									}	
								}
							  }
								pairwise2++;
							}
						  
						pairwise1++;
						}
						for (int k=1;k<=tP[0];k++){
							for (int l=k+1;l<=tP[0];l++){
								if (tP[l]==tP[k]) tP[l]=-1;
							}
							if (tP[k]!=-1) {
								ret[retc]=iP[tP[k]];
								retc++;
							}
						}
					}
					//now = System.currentTimeMillis();
					//IJ.write ("Arraycopy:"+(now-now2)+"ms");
				}
			}
		}
		//drawArray16(tmOut,W,H,"Swallow");
		filteredParticle[] tmP=new filteredParticle[retc];
		System.arraycopy(ret, 0, tmP, 0, retc);
		ret=tmP;
		//IJ.write(ret.length+"+"+retc);
		return ret;
	}
	boolean chkNghbr(boolean[] P1, int index, int W, int H){
		if (((index-1>=0)&&(P1[index-1]))||((index+1<W*H)&&(P1[index+1]))||((index-W>=0)&&(P1[index-W]))||((index+W<W*H)&&(P1[index+W]))) return true;
		else return false;
	}
	int[] isIntersect(Particle P1, Particle P2){
		int[] ret={-1,-1,-1,-1};
		int X0=(P1.boundX<P2.boundX)?P1.boundX:P2.boundX;
		int X1=(P1.boundX+P1.boundW>P2.boundX+P2.boundW)?P1.boundX+P1.boundW:P2.boundX+P2.boundW;
		int Y0=(P1.boundY<P2.boundY)?P1.boundY:P2.boundY;
		int Y1=(P1.boundY+P1.boundH>P2.boundY+P2.boundH)?P1.boundY+P1.boundH:P2.boundY+P2.boundH;
		int cW=X1-X0;
		int cH=Y1-Y0;
		int sW=P1.boundW+P2.boundW;
		int sH=P1.boundH+P2.boundH;
		//IJ.write("X0:"+X0+"X1:"+X1+"Y0:"+Y0+"Y1:"+Y1);
		//IJ.write("cW:"+cW+"cH:"+cH+"sW:"+sW+"sH:"+sH);
		if ((cW<=sW)&&(cH<=sH)&&((cW!=sW)&&(cH!=sH))) {
			ret[0]=X0;
			ret[1]=Y0;
			ret[2]=cW;
			ret[3]=cH;
		}
		return ret;
	}
	void groupParticles(filteredParticle[] iP, int[]tP, int[] waterlevel, int W, int offset, int max, int carea){
		
		if (waterlevel[offset]>9){
			tP[0]++;
			// IJ.write(waterlevel[offset]+"");
			tP[tP[0]]=waterlevel[offset]-10;
			
		}
		carea++;
		//IJ.write(carea+"");
		waterlevel[offset]=1;
		if (carea<=maxSteps){
			int index=offset-W;
			if ((index>=0)&&(waterlevel[index]>8)) {
				groupParticles(iP,tP,waterlevel,W,index,max,carea);
			}
			index=offset+W;
			if ((index<max)&&(waterlevel[index]>8)) 
			{
				groupParticles(iP,tP,waterlevel,W,index,max,carea);
			}
			index=offset-1;
			if ((offset%W>0)&&(index>=0)&&(waterlevel[index]>8))
			{
				groupParticles(iP,tP,waterlevel,W,index,max,carea);
			} 
			index=offset+1;
			if ((index%W>0)&&(index<max)&&(waterlevel[index]>8)) {
				groupParticles(iP,tP,waterlevel,W,index,max,carea);
			}
		}
		
			return; 
	}

	filteredParticle[] walkAgainstWater(int[] finalWater2, float[] bckg, int[]waterlevel, int[] waterFlow, int[] fpxs, int W, int max,int est){
		filteredParticle[] P=new filteredParticle[est];
		int i=0;
		int j=0;
		int k=0;
		int index=0;
		int index2=0;
		int cnt=0;
		int ngbh=0;		
		//int intDens=0;
		int pC=0;
		int p=getPercentileValue(finalWater2,dynamics);
		
		for (i=0;i<max;i++){
			if ((waterlevel[i]>1)&&(finalWater2[i]>=p)){
				boolean[] tmpFlow=new boolean[max];
				int[] cSteps={0};
				wAW3(tmpFlow,waterFlow,i,W,max, cSteps);
				int[]coords=getParticleSizeBool(tmpFlow,W,H, cSteps[0], i%W,i/W);
				if ((coords[2]>0)&&(coords[3]>0)){
					P[pC]=new filteredParticle(0.0,0.0,coords[4],coords[0],coords[1],coords[2],coords[3]);
					P[pC].brightest=finalWater2[i];
					P[pC].brightestX=i%W;
					P[pC].brightestY=i/W;
					//intDens=0;
					int addV=0;
				
					for (j=coords[1];j<coords[1]+coords[3];j++){
						for (k=coords[0];k<coords[0]+coords[2];k++){
							index=j*W+k;
							index2=(j-coords[1])*coords[2]+k-coords[0];
							if (tmpFlow[index]){
								//addV=(finalWater2[index]-(int)bckg[index]<0)?0:finalWater2[index]-(int)bckg[index];
								//intDens+=finalWater2[index];
								P[pC].pxs[index2]=finalWater2[index];
								P[pC].filtered_pxs[index2]=fpxs[index];
								//tmpOut[index]=rndValue;

							}
							
						}
					}
					//P[pC].weight=intDens;
					
					waterFlow[i]=10+pC;
					pC++;
					if (pC>=P.length) {
						filteredParticle[] tP=new filteredParticle[2*pC];
						System.arraycopy(P, 0, tP, 0, P.length);
        				P = tP;
    				}
				}
			}
		}

		filteredParticle[] tP=new filteredParticle[pC];
		System.arraycopy(P, 0, tP, 0, pC);
        P = tP;
		return P;
	}
	void wAW3(boolean[]tmpflow,int[] waterFlow, int offset, int w, int max, int[] cSteps){
		waterFlow[offset]=9;
		tmpflow[offset]=true;
		cSteps[0]++;
		int index=offset-w-1;
		if ((index>=0)&&(waterFlow[index]==-4)) wAW3(tmpflow,waterFlow,index,w,max, cSteps);
		index=offset-w;
		if ((index>=0)&&(waterFlow[index]==-3)) wAW3(tmpflow,waterFlow,index,w,max, cSteps);
		index=offset-w+1;
		if ((index>=0)&&(waterFlow[index]==-2)) wAW3(tmpflow,waterFlow,index,w,max, cSteps);
		index=offset-1;
		if ((index>=0)&&(waterFlow[index]==-1)) wAW3(tmpflow,waterFlow,index,w,max, cSteps);
		index=offset+1;
		if ((index<max)&&(waterFlow[index]==1)) wAW3(tmpflow,waterFlow,index,w,max, cSteps);
		index=offset+w-1;
		if ((index<max)&&(waterFlow[index]==2)) wAW3(tmpflow,waterFlow,index,w,max, cSteps);
		index=offset+w;
		if ((index<max)&&(waterFlow[index]==3)) wAW3(tmpflow,waterFlow,index,w,max, cSteps);
		index=offset+w+1;
		if ((index<max)&&(waterFlow[index]==4)) wAW3(tmpflow,waterFlow,index,w,max, cSteps);
		return;
	}
	
	void getInitialGroup(int[] water, Seeds group, int offset, int w, int max){
		
		group.add(offset%w,offset/w, 1);
		water[offset]=0;
		int index=offset-w;
		if ((index>=0)&&(water[index]>0)) getInitialGroup(water,group,index,w,max);
		index=offset+w;
		if ((index<max)&&(water[index]>0)) getInitialGroup(water,group,index,w,max);	
		index=offset-1;
		if ((index>=0)&&(water[index]>0)) getInitialGroup(water,group,index,w,max);
		index=offset+1;
		if ((index<max)&&(water[index]>0)) getInitialGroup(water,group,index,w,max);
		index=offset-w-1;
		if ((index>=0)&&(water[index]>0)) getInitialGroup(water,group,index,w,max);
		index=offset-w+1;
		if ((index>=0)&&(water[index]>0)) getInitialGroup(water,group,index,w,max);
		index=offset+w-1;
		if ((index<max)&&(water[index]>0)) getInitialGroup(water,group,index,w,max);
		index=offset+w+1;
		if ((index<max)&&(water[index]>0)) getInitialGroup(water,group,index,w,max);
		return;
	}
	int[] getParticleSizeBool(boolean[]pxs, int W, int H, int cSteps, int offX, int offY){
	int minX=W,minY=H,maxX=0,maxY=0,area=0,i,j;
	int[] limits={offY-cSteps,offY+cSteps,offX-cSteps,offX+cSteps};
        if (limits[0]<0) limits[0]=0;
        if (limits[1]>H) limits[1]=H;
        if (limits[2]<0) limits[2]=0;
        if (limits[3]>W) limits[3]=W;
        //printf ("%d,%d: %d,%d\n",limits[0],limits[1],limits[2],limits[3]);
	for (i=limits[0];i<limits[1];i++){
		for (j=limits[2];j<limits[3];j++){
			if (pxs[j+i*W]) {
				area++;
				if (j<minX) minX=j;
				if (j>maxX) maxX=j;
				if (i<minY) minY=i;
				if (i>maxY) maxY=i;
			}
		}
	}
	
	int[] d= {minX,minY,maxX-minX+1,maxY-minY+1,area};
	return d;
	}
	double[] avgandSD(double[] ay, int N){
		double avg=0,SD=0;
		int N0=0;
		//IJ.write(N+";"+ay[0]);
		if (N>0){
			for (int i=0;i<N;i++) if (ay[i]!=-1.0) {avg+=ay[i];N0++;}
			avg=avg/(N0);
			for (int i=0;i<N;i++) if (ay[i]!=-1.0) SD+=Math.pow(ay[i]-avg,2);
			SD=Math.sqrt(SD/(N0));
			}
		else if (N==0) {
			avg=ay[0];
			SD=0;
			}
		double[]a={avg,SD};
		//IJ.write(avg+":"+SD);
		return a;
	}
	void drawArray(int[] ipxs,int W, int H, String nme){
		//byte[] pxs=(byte[]) stack.getPixels(frame);
		ImagePlus Path2D = NewImage.createByteImage(nme, W, H,1, NewImage.FILL_BLACK);
		ImageProcessor cp2D=Path2D.getProcessor();	
		byte[] pxs=(byte[])cp2D.getPixels();
		for (int i=0;i<W*H;i++) pxs[i]=(byte)ipxs[i];
		Path2D.updateAndDraw();
		Path2D.show();
		return;
	}
	void drawArray16(int[] ipxs,int W, int H, String nme){
		//byte[] pxs=(byte[]) stack.getPixels(frame);
		ImagePlus Path2D = NewImage.createShortImage(nme, W, H,1, NewImage.FILL_BLACK);
		ImageProcessor cp2D=Path2D.getProcessor();	
		short[] pxs=(short[])cp2D.getPixels();
		for (int i=0;i<W*H;i++) pxs[i]=(short)ipxs[i];
		Path2D.updateAndDraw();
		Path2D.show();
		return;
	}
	void drawArrayFloat(float[] fpxs,int W, int H, String nme){
		//byte[] pxs=(byte[]) stack.getPixels(frame);
		ImagePlus Path2D = NewImage.createByteImage(nme, W, H,1, NewImage.FILL_BLACK);
		ImageProcessor cp2D=Path2D.getProcessor();	
		byte[] pxs=(byte[])cp2D.getPixels();
		for (int i=0;i<W*H;i++) pxs[i]=(byte)fpxs[i];
		Path2D.updateAndDraw();
		Path2D.show();
		return;
	}
	void drawArrayFloat16(float[] fpxs,int W, int H, String nme){
		//byte[] pxs=(byte[]) stack.getPixels(frame);
		ImagePlus Path2D = NewImage.createShortImage(nme, W, H,1, NewImage.FILL_BLACK);
		ImageProcessor cp2D=Path2D.getProcessor();	
		short[] pxs=(short[])cp2D.getPixels();
		for (int i=0;i<W*H;i++) pxs[i]=(short)fpxs[i];
		Path2D.updateAndDraw();
		Path2D.show();
		return;
	}
	void drawParticles(filteredParticle[] fp, int W, int H, String nme){
		ImagePlus Path2D = NewImage.createShortImage(nme, W, H,1, NewImage.FILL_BLACK);
		ImageProcessor cp2D=Path2D.getProcessor();	
		short[] pxs=(short[])cp2D.getPixels();
		//IJ.log(fp.length+"");
		for (int i=0;i<fp.length;i++){
			pxs[(int)fp[i].boundX+(int)fp[i].boundY*W]=(short)fp[i].area;
		}
		Path2D.updateAndDraw();
		Path2D.show();
	}
	int getPercentileValue(int[] fpxs, double p){
		int[] histo=getHistogram(fpxs);
		int i=0;
		int p98=0;
		int max=(int)((double)(W*H)*p);
		while (p98<max) {
			p98+=histo[i];
			i++;
		}
		p98=i-1;
		//IJ.log("p"+(p*100)+":"+p98+"");
		return p98;
	}
	int[] getHistogram(int[] fpxs){
		int[] h=new int[2<<(imgType-1)];
		for (int i=0;i<W*H;i++) h[fpxs[i]]++;
		return h;
	}
}