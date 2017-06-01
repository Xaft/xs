package prat;

import java.lang.*;

public class filteredParticle extends Particle {
	public int[] filtered_pxs;
	private boolean edgesDetected;
	public filteredParticle(){
		super();
		filtered_pxs=new int[boundW*boundH];
		edgesDetected=false;
	}
	public filteredParticle(double cX, double cY, int a, int X, int Y, int W, int H){
		super(cX,  cY,  a, X,  Y, W, H);
		filtered_pxs=new int[boundW*boundH];
		edgesDetected=false;
	}
	public void getCenteredByFiltered(int rad){
		int Y=0;
		int X=0;
		int index=0;
		double[] weightH=new double[this.boundW];
		double[] weightV=new double[this.boundH];
		double sum=0, tmp=0;
		int YlLm=(this.brightestY-this.boundY-rad<0)?0:this.brightestY-this.boundY-rad;
		int XlLm=(this.brightestX-this.boundX-rad<0)?0:this.brightestX-this.boundX-rad;
		int YuLm=(this.brightestY-this.boundY+rad<this.boundH-1)?this.brightestY-this.boundY+rad:this.boundH-1;
		int XuLm=(this.brightestX-this.boundX+rad<this.boundW-1)?this.brightestX-this.boundX+rad:this.boundW-1;
		for (int i=YlLm;i<=YuLm;i++){
			for (int j=XlLm;j<=XuLm;j++){
				index=i*this.boundW+j;
				weightV[i]+=filtered_pxs[index];
				weightH[j]+=filtered_pxs[index];
				//n++;
			}
		}
		for (int j=XlLm;j<=XuLm;j++){
			tmp+=weightH[j];
			sum+=j*weightH[j];
		}
		if (tmp==0) this.centerX=this.boundX+0.0;
		else this.centerX=this.boundX+sum/tmp;
		tmp=0;sum=0;
		for (int i=YlLm;i<=YuLm;i++){
			tmp+=weightV[i];
			sum+=i*weightV[i];
		}
		if (tmp==0) this.centerY=this.boundY+0.0;
		else this.centerY=this.boundY+sum/tmp;
		this.intcX=(int)Math.round(this.centerX);
		this.intcY=(int)Math.round(this.centerY);
	}
	public void swallow(filteredParticle P, int[] newcoords){
		
		if (brightest<P.brightest){
			brightest=P.brightest;
			brightestX=P.brightestX;
			brightestY=P.brightestY;
		}
		int Xoffset=boundX-newcoords[0];
		int Yoffset=boundY-newcoords[1];
		int[] tmpxs=new int[newcoords[2]*newcoords[3]];
		int[] tmfpxs=new int[newcoords[2]*newcoords[3]];
		for (int i=0;i<boundH;i++){
			for (int j=0;j<boundW;j++){
				tmpxs[(i+Yoffset)*newcoords[2]+j+Xoffset]=pxs[i*boundW+j]; 
				tmfpxs[(i+Yoffset)*newcoords[2]+j+Xoffset]=filtered_pxs[i*boundW+j]; 
			}
		}
		Xoffset=P.boundX-newcoords[0];
		Yoffset=P.boundY-newcoords[1];
		for (int i=0;i<P.boundH;i++){
			for (int j=0;j<P.boundW;j++){
				tmpxs[(i+Yoffset)*newcoords[2]+j+Xoffset]+=P.pxs[i*P.boundW+j]; 
				tmfpxs[(i+Yoffset)*newcoords[2]+j+Xoffset]+=P.filtered_pxs[i*P.boundW+j]; 
			}
		}
		pxs=tmpxs;
		filtered_pxs=tmfpxs;
		boolean[] tmedges=new boolean[newcoords[2]*newcoords[3]];
		edges=tmedges;
		boundX=newcoords[0];
		boundY=newcoords[1];
		boundW=newcoords[2];
		boundH=newcoords[3];
		area+=P.area;
		merged=true;
		edgesDetected=false;
	}
}