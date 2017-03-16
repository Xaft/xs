package prat;

import java.lang.*;
import java.lang.Math;
import ij.*;
import java.awt.Shape;
import java.awt.Polygon;
import ij.measure.CurveFitter;
import ij.gui.Roi;
import ij.gui.PolygonRoi;


public class Particle {
	public double centerX;
	public double centerY;
	public int intcX;
	public int intcY;
	public int area;
	public int boundX;
	public int boundY;
	public int boundW;
	public int boundH;
	public int brightestX;
	public int brightestY;
	public float bckg;
	public float[] bckgs;
	public int[] pxs;
	
	public boolean[] edges;
	int edgeW;
	public int weight;
	public int[] weights;
	public int brightest;
	public boolean merged;
	private boolean edgesDetected;
	private boolean edgesInner=false;
	public Shape shpe;
	public float circularity;
	public double R;
	public int comment;
	private int pcnt;
	private int pmax;
	private double bgValue;
	public boolean ghost;
public Particle(){
	centerX=0.0;
	centerY=0.0;
	intcX=0;
	intcY=0;
	area=0;
	boundX=0;
	boundY=0;
	boundW=0;
	boundH=0;
	edgeW=0;
	pxs=new int[boundW*boundH];

	edges=new boolean[boundW*boundH];	
	brightestX=0;
	brightestY=0;
	brightest=0;
	bckg=0f;
	weight=0;
	merged=false;
	shpe=new Polygon();
	edgesDetected=false;
	circularity=0f;
	R=0.0;
	comment=0;
	pcnt=0;
	pmax=0;
	bgValue=-1.0;
	ghost=true;
	}
public Particle(double cX, double cY, int a, int X, int Y, int W, int H){
		centerX=cX;
		centerY=cY;
		intcX=(int)Math.round(cX);
		intcY=(int)Math.round(cY);
		area=a;
		boundX=X;
		boundY=Y;
		boundW=W;
		boundH=H;
		edgeW=W;
		pxs=new int[boundW*boundH];
		edges=new boolean[boundW*boundH];	
		brightestX=(int)Math.floor(cX);
		brightestY=(int)Math.floor(cY);
		brightest=0;
		bckg=0f;
		bckgs=new float[32];
		weight=a;
		weights=new int[32];
		merged=false;
		shpe=new Polygon();
		edgesDetected=false;
		circularity=0f;
		R=Math.sqrt((double)a/Math.PI);
		comment=0;
		pcnt=0;
		pmax=boundW*boundH;
		bgValue=-1.0;
		ghost=false;
		}
public int next(){
	return pcnt<pmax?pxs[pcnt++]:-1;
}
public int prev(){
	return pcnt>=0?pxs[pcnt--]:-1;
}
public void next(int p){
	if (pcnt<pmax) pxs[pcnt++]=p;
}
public void prev(int p){
	if (pcnt>=0) pxs[pcnt--]=p;
}
public void resetPcnt(){
	pcnt=0;
}
public void setPcnt(int v){
	if (v>=0&&v<pmax) pcnt=v;
}
public void swallow(Particle P, int[] newcoords){
		
		if (brightest<P.brightest){
			brightest=P.brightest;
			brightestX=P.brightestX;
			brightestY=P.brightestY;
		}
		int Xoffset=boundX-newcoords[0];
		int Yoffset=boundY-newcoords[1];
		int[] tmpxs=new int[newcoords[2]*newcoords[3]];
		for (int i=0;i<boundH;i++){
			for (int j=0;j<boundW;j++){
				tmpxs[(i+Yoffset)*newcoords[2]+j+Xoffset]=pxs[i*boundW+j]; 
			}
		}
		Xoffset=P.boundX-newcoords[0];
		Yoffset=P.boundY-newcoords[1];
		for (int i=0;i<P.boundH;i++){
			for (int j=0;j<P.boundW;j++){
				tmpxs[(i+Yoffset)*newcoords[2]+j+Xoffset]+=P.pxs[i*P.boundW+j]; 
			}
		}
		pxs=tmpxs;
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
public void trimByBgValue(float contrast){
	if (bgValue>=0){
		int max=boundW*boundH;
		int tresh=(int)(bgValue*contrast);
		//IJ.log(tresh+"");
		for (int i=0;i<max;i++) {
			if ((pxs[i]>0)&&(pxs[i]<tresh)) {
				pxs[i]=0;
				area--;
			}
		}
	}
	
}
public void trimPercentofMax(float percent){
	int max=boundW*boundH;
	int tresh=(int)(brightest*percent);
	for (int i=0;i<max;i++) {
		if ((pxs[i]>0)&&(pxs[i]<tresh)) {
			pxs[i]=0;
			area--;
		}
	}
	
}
public void trim(int ngbhStatus){
		int[] pos={-boundW-1,-boundW,-boundW+1,-1,1,boundW-1,boundW,boundW+1};
		int max=boundW*boundH;
		int cnt=0;  
		int ngbh=0;
		int[] tmpxs=new int[max];
		boolean trimmed=false;
		do{
		  trimmed=false;
		  for (int index=0;index<max;index++){
			if (pxs[index]!=0){
				cnt=0;
				ngbh=0;
				do{
					if ((index+pos[cnt]>0)&&(index+pos[cnt]<max)&&(pxs[index+pos[cnt]])!=0) ngbh++;
					cnt++;
					//IJ.write(cnt+"");
				}while((ngbh<ngbhStatus)&&(cnt<8));
				if (ngbh<ngbhStatus) {
					tmpxs[index]=0;
					area--;
					trimmed=true;
				}
				else tmpxs[index]=pxs[index];
			}
		  }
		pxs=tmpxs;
		}while(trimmed);
		
	}
public void getWeight(){
	weight=0;
	area=0;
	for (int i=0;i<boundW*boundH;i++) {
		weight+=pxs[i];
		if (pxs[i]!=0) area++;
		}
	//if (area!=0) weight=(int)(weight/area);
	if (area<4) weight=0; 
	R=Math.sqrt((double)area/Math.PI);
	weights[0]=weight;
	}
public void getEdges(){
	getEdges(true);
}
public void getEdges(boolean inner){
	int bW=boundW+2;
	int bH=boundH+2;
	boolean[]tpxs=new boolean[(bW+2)*(bH+2)];
	boolean[] tmedges=new boolean[bW*bH];
	for (int i=1;i<(bW+2)*(bH+2);i++) tpxs[i]=!inner;
	for (int i=2;i<bH;i++){
		for (int j=2;j<bW;j++){
			tpxs[i*(bW+2)+j]=(pxs[(i-2)*boundW+j-2]>0)?inner:!inner;
		}
	}
	//int[] relcoords={-boundW-1,-boundW,-boundW+1,-1,1,boundW-1,boundW,boundW+1};
	//int[] relcoords={-boundW-3,-boundW-2,-boundW-1,-1,1,boundW+1,boundW+2,boundW+3};
	int[] relcoords={-bW-2,-1,1,bW+2};
	//int dges=0;
	for (int i=1;i<bH+1;i++){
		for (int j=1;j<bW+1;j++){
			int index=i*(bW+2)+j;
			if (tpxs[index]){
				int c=0;
				boolean res=true;
				while ((c<relcoords.length)&&(res)){
					res&=tpxs[index+relcoords[c]];
					c++;	
				}
				if (!res){
					tmedges[(i-1)*bW+j-1]=true;
					//dges++;
				}
			}
		}
	}
	if (inner){
		for (int i=1;i<boundH+1;i++){
			for (int j=1;j<boundW+1;j++){
				int index=i*bW+j;
				edges[(i-1)*boundW+j-1]=tmedges[index];
			}
		}
		edgeW=boundW;
	}
	else {
		edges=tmedges;
		edgeW=bW;
	}
	/*if (dges>0&&area>0){
		circularity=(float)(4*Math.PI*(area/(Math.pow(dges,2))));
	}*/
	edgesInner=inner;
	edgesDetected=true;
}
public int getEdgeW(){
	return edgeW;
}
public void getBgValue(float[] bg, int w, int h){
	if (!edgesInner&&edgesDetected){
		int N=0;
		bgValue=0.0;
		//IJ.log(boundW+"-"+boundH+":"+edgeW);
		for (int m=(boundY<1?0:boundY-1);m<(boundY+boundH<h-1?boundY+boundH+1:h);m++){
			for (int n=(boundX<1?0:boundX-1);n<(boundX+boundW<w-1?boundX+boundW+1:w);n++){
				//IJ.log(m+","+n+"="+((m-boundY+1)*(edgeW)+(n-boundX+1))+":"+edges.length);
				if (edges[(m-boundY+1)*(edgeW)+(n-boundX+1)]) {
					bgValue+=bg[m*w+n];
					N++;
				}
			}
		}
		bgValue/=N;
		//IJ.log(bgValue+"");
	}
}
public void getCircularity(){
	if (this.shpe==null) this.circularity=0f;
	else {
		PolygonRoi p=new PolygonRoi((Polygon)(this.shpe),Roi.POLYGON);
		if (p.getNCoordinates()>2){
			double dges=p.getLength();
			circularity=(dges==0.0)?0f:(float)(4*Math.PI*(area/(Math.pow(dges,2))));
		}
		else this.circularity=0f;
	}
}
/*public boolean getShape(){
	return getShape(true)[0];
}
public boolean getShape(boolean inner){
	return getShape(inner)[0];
}*/
public boolean getShape(){
	return getShape(true);
} 
public boolean getShape(boolean inner){
	if (!edgesDetected) this.getEdges(inner);

	//IJ.log("GetShape");
	int max=edges.length;
	//IJ.log(edgeW+"-"+boundH+":"+(max/edgeW));
	int edgeH=edges.length/edgeW;
	int[][] tcoords=new int[2][max];
	boolean[] tmpedges=new boolean[max];
	for (int i=0;i<max;i++) tmpedges[i]=edges[i];
	int cnt=0;
	int offset=-1;
	int[] stepsX={+1,0,-1,0,+1,+1,-1,-1};
	int[] stepsY={0,+1,0,-1,-1,+1,-1,+1};
	int ngbhs=0;
	while ((offset<max)&&ngbhs<2){
		offset++;
		while ((offset<max)&&(!tmpedges[offset])) offset++;
		if (offset<max){
			ngbhs=0;
			int indX=offset%edgeW;
			int indY=offset/edgeW;
			for (int intC=0;intC<8;intC++){
				int iindX=indX+stepsX[intC];
				int iindY=indY+stepsY[intC];
				if ((iindX>=0)&&(iindX<edgeW)&&(iindY>=0)&&(iindY<edgeH)&&(tmpedges[iindX+iindY*edgeW])) ngbhs++;
			}
		}
	}
	//IJ.log(offset+""+max);
	boolean lost=false;
	if (offset<max){
	int indX=offset%edgeW;
	int indY=offset/edgeW;
	tcoords[0][cnt]=boundX+indX;
	tcoords[1][cnt]=boundY+indY;
	tmpedges[offset]=false;
	cnt++;
	
	int[] ngbh=new int[2];
	int nC=0;
	int iindX=0;
	int iindY=0;
	for (int intC=0;intC<8;intC++){
		iindX=indX+stepsX[intC];
		iindY=indY+stepsY[intC];
		if ((iindX>=0)&&(iindX<edgeW)&&(iindY>=0)&&(iindY<edgeH)&&(tmpedges[iindX+iindY*edgeW])) {
			ngbh[nC]=iindX+iindY*edgeW;
			nC++;
		}
	}
	/*indX=iindX;
	indY=iindY;
	tcoords[0][cnt]=boundX+indX;
	tcoords[1][cnt]=boundY+indY;
	cnt++;
	int index=iindX+iindY*edgeW;
	tmpedges[index]=false;*/
	offset=ngbh[1];
	int index=ngbh[0];
	indX=ngbh[0]%edgeW;
	indY=ngbh[0]/edgeW;
	//IJ.log(max+"");
	do{
		int intC=0;
		iindX=indX;
		iindY=indY;
		for (intC=0;intC<8;intC++){
			iindX=indX+stepsX[intC];
			iindY=indY+stepsY[intC];
			if ((iindX>=0)&&(iindX<edgeW)&&(iindY>=0)&&(iindY<edgeH)&&(tmpedges[iindX+iindY*edgeW])) break;
		}
		//IJ.log(intC+"a");
		
		if (intC<8) {
			tcoords[0][cnt]=boundX+indX;
			tcoords[1][cnt]=boundY+indY;
			cnt++;
			index=iindX+iindY*edgeW;
			indX=iindX;
			indY=iindY;
			//IJ.log(offset+"-"+index);

			
		}
		else{
		cnt--;
		indX=tcoords[0][cnt]-boundX;
		indY=tcoords[1][cnt]-boundY;
		lost=true;
		}
		tmpedges[index]=false;
	}
	while (index!=offset);
	/*for (int i=0;i<boundH;i++){
		for (int j=0;j<boundW;j++){
			if (edges[i*boundW+j]) {
				tcoords[0][cnt]=boundX+j;
				tcoords[1][cnt]=boundY+i;
				cnt++;
			}
		}
	}*/
	//IJ.log("end");
	if (cnt>2) {
		if (edgeW!=boundW){
			for (int i=0;i<cnt;i++){
				tcoords[0][i]--;tcoords[1][i]--;
			}
		}
		shpe=new Polygon(tcoords[0],tcoords[1],cnt);
	}
	else {
		tcoords[0][0]=boundX;tcoords[1][0]=boundY;
		tcoords[0][1]=boundX+boundW;tcoords[1][1]=boundY;
		tcoords[0][2]=boundX+boundW;tcoords[1][2]=boundY+boundH;
		tcoords[0][3]=boundX;tcoords[1][3]=boundY+boundH;
		shpe=new Polygon(tcoords[0],tcoords[1],4);
		return false;
		//tmpedges[0]=true;
		//return tmpedges;
	}
	/*else {
		int[] xs={boundX, boundX+boundW,boundX+boundW,boundX};
		int[] ys={boundY,boundY,boundY+boundH,boundY+boundH};
		shpe=new Polygon(xs,ys,4);
	}*/
	
	}
		else {
		tcoords[0][0]=boundX;tcoords[1][0]=boundY;
		tcoords[0][1]=boundX+boundW;tcoords[1][1]=boundY;
		tcoords[0][2]=boundX+boundW;tcoords[1][2]=boundY+boundH;
		tcoords[0][3]=boundX;tcoords[1][3]=boundY+boundH;
		shpe=new Polygon(tcoords[0],tcoords[1],4);
		return false;
		//return true;
	}
	//tmpedges[0]=true;
	return true;
	//return false;
	
}
public void getShapeByTranslation(Particle P){
	if (P.edgesDetected&&P.shpe!=null){
		this.shpe=P.shpe;
		Polygon tP=(Polygon)(this.shpe);
		tP.translate(this.boundX-P.boundX, this.boundY-P.boundY);
		this.shpe=tP;
		
	}
}
public Particle[] walkAround(int[]pxs, int x, int y, int W, int H, int bg){
	int[] tmpParticle=new int[W*H];
	int dx=x; int dy=0;
	walkIn(pxs,tmpParticle,W,H,W*H,x,y,bg);			
	int[] pDimensions = getParticleSize(tmpParticle,W,H);
	Particle[] coords=new Particle[1];
	if (pDimensions[2]<1) coords[0]=new Particle(0,0,0,0,0,0,0);
	else { 
		int[] foundParticle=new int[pDimensions[2]*pDimensions[3]];
		for (int i=pDimensions[1];i<pDimensions[1]+pDimensions[3];i++){
			for (int j=pDimensions[0];j<pDimensions[0]+pDimensions[2];j++){
				if (tmpParticle[j+i*W]>bg) {
						foundParticle[(j-pDimensions[0])+(i-pDimensions[1])*pDimensions[2]]=tmpParticle[j+i*W];
					
					}
				
				}
		
		}
	
		if (pDimensions[2]*pDimensions[3]>21) {
			int[] seeds=getSeeds(foundParticle, pDimensions[2],pDimensions[3]);
		
			separateSeeds(foundParticle,seeds,pDimensions[2],pDimensions[3],-1);
		
			if ((seeds[0]>2)){
				
				coords=new Particle[seeds[0]];
				for (int i=0;i<seeds[0];i++){
					
					int[] tP=new int [pDimensions[2]*pDimensions[3]];
					walkIn(foundParticle,tP,pDimensions[2],pDimensions[3],pDimensions[2]*pDimensions[3],seeds[i*2+1],seeds[i*2+2],bg);
					int[] tcoords=getParticleSize(tP,pDimensions[2],pDimensions[3]);
					tcoords[0]+=pDimensions[0];tcoords[1]+=pDimensions[1];
					double[] center={0,0};
					if (tcoords[2]>0) center=getCenter(tP,tcoords[2],tcoords[3]);
					coords[i]=new Particle(center[0]+tcoords[0],center[1]+tcoords[1],tcoords[4],tcoords[0],tcoords[1],tcoords[2],tcoords[3]);
					}
		
				}
				else {
					double[] center=getCenter(foundParticle,pDimensions[2],pDimensions[3]);
					coords[0]=new Particle(center[0]+pDimensions[0],center[1]+pDimensions[1],pDimensions[4],pDimensions[0],pDimensions[1],pDimensions[2],pDimensions[3]);
				}	
			}
			else {
				double[] center=getCenter(foundParticle,pDimensions[2],pDimensions[3]);
				coords[0]=new Particle(center[0]+pDimensions[0],center[1]+pDimensions[1],pDimensions[4],pDimensions[0],pDimensions[1],pDimensions[2],pDimensions[3]);
			}	
		}
	return coords;

}

public void walkIn (int[]pxs, int[] tP, int W, int H, int max, int X, int Y,int bg){
	int offset=X+W*Y;
	tP[offset]=pxs[offset];
	pxs[offset]=0;
	int index=offset-W;
	if ((index>=0)&&(pxs[index]!=bg)) {
		walkIn(pxs,tP,W,H,max,X,Y-1,bg);
	}
	index=offset+W;
	if ((index<max)&&(pxs[index]!=bg)) 
	{
		walkIn(pxs,tP,W,H,max,X,Y+1,bg);
	}
	index=offset-1;
	if ((offset%W>0)&&(index>=0)&&(pxs[index]!=bg))
	{
		
		walkIn(pxs,tP,W,H,max,X-1,Y,bg);
	 } 
	index=offset+1;
	if ((index%W>0)&&(index<max)&&(pxs[index]!=bg)) {
		
		walkIn(pxs,tP,W,H,max,X+1,Y,bg);
	}
	return; 
	}

public int[] getParticleSize(int[]pxs, int W, int H){
	int minX=W,minY=H,maxX=0,maxY=0,area=0;
	for (int i=0;i<H;i++){
		for (int j=0;j<W;j++){
			if (pxs[j+i*W]!=0) {
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

public int[] getSeeds(int[] pxs, int W, int H){
	int[] pys=new int[W*H];
	for (int i=0;i<W*H;i++) pys[i]=pxs[i];
	int[] tmp=new int[9];
	int a=0;
	for (int i=0;i<H-2;i++){
		for (int j=0;j<W-2;j++){
			int[] order={ 0, 1, 2, 3, 4, 5, 6, 7, 8 };
			for (int k=0;k<=2;k++){
				for (int l=0;l<=2;l++){
					tmp[(k)*3+l]=pys[(i+k)*W+(j+l)];
				}
			}
			for (int k=1;k<9;k++){
				int l=k;
				while ((l>0)&&(tmp[l]<tmp[l-1])) {
						a=order[l-1];
						order[l-1]=order[l];
						order[l]=a;
						a=tmp[l-1];
						tmp[l-1]=tmp[l];
						tmp[l]=a;
						l--;
					}
			}
			for (int k=0;k<5;k++){
				pys[(i+order[k]/3)*W+(j+order[k]%3)]=0;
			}
		}
	}
	int maxIndex=H*W;
	int[] retvalue= new int[1000];
	int retindex=1;
	for (int i=0;i<H;i++){
		for (int j=0;j<W;j++){
			if (pys[i*W+j]>0) {
				int weight=0;
				for (int k=-1;k<=1;k++){
					for (int l=-1;l<=1;l++){
						int index=(i+k)*W+(j+l);
						if ((index>=0)&&(index<maxIndex)&&(pys[index]>0)) weight++;
					}
				}
			if (weight>=4) {
				int dx=0,dy=0;
				weight=0;
				for (int k=-1;k<=1;k++){
					int index=(i+k)*W+(j+2); 
					if ((index>=0)&&(index<maxIndex)&&(pys[index]>0)) weight++;
				 }
				if (weight>0) dx++;
				weight=0;
				for (int l=-1;l<=1+dx;l++){
					int index=(i+2)*W+(j+l);
					if ((index>=0)&&(index<maxIndex)&&(pys[index]>0)) weight++;
				}
				if (weight>0) dy++;
				if (pys[j+dx+(i+dy*W)]>0) {
					retvalue[retindex]=j+dx;
					retvalue[retindex+1]=i+dy;
				}
				else {
					retvalue[retindex]=j;
					retvalue[retindex+1]=i;
				}
				retindex+=2;
				for (int k=-1;k<=1+dy;k++){
					for (int l=-1;l<=1+dy;l++){
						int index=(i+k)*W+(j+l);
						if ((index>=0)&&(index<maxIndex)) pys[index]=0;
						}
					}
				}	
			}
		}
	}
	retvalue[0]=(retindex-1)/2;
	return retvalue;
}
public void reduceSeeds(int[] pxs, int[] seeds, int W, int H){
	boolean theSame=false;
	int i=0,j=0,index=0;
	do{
		j=i+1;
	 	do{
				boolean tS=false;
				int sX1=seeds[(i*2)+1],sY1=seeds[(i*2)+2];
				int sX2=seeds[(j*2)+1],sY2=seeds[(j*2)+2];
				int[] min=getMinalongLine(pxs,sX1,sY1,sX2,sY2,W);
				if (min[0]<0) {
					for (int k=i*2+1;k<seeds[0]*2-1;k++) seeds[k]=seeds[k+2];
					seeds[0]--;		
				}
				else if ((min[0]>0.45*(pxs[sY1*W+sX1]+pxs[sY2*W+sX2]))) {
					//seeds[(i*2)+1]+=(int)((sX2-sX1)/2);
					//seeds[(i*2)+2]+=(int)((sY2-sY1)/2);
					//i=0;
					for (int k=j*2+1;k<seeds[0]*2-1;k++) seeds[k]=seeds[k+2];
					seeds[0]--;					
					}
				j++;
			}while (j<seeds[0]);
			i++;
		}while(i<seeds[0]);
	}

public int separateSeeds (int[] pxs, int[] seeds, int W, int H, int ws){
	//int iter=0;
	int i=0,j=0,index=0;
	
	if (seeds[0]==2){
		int sX1=seeds[1],sY1=seeds[2];
		int sX2=seeds[3],sY2=seeds[4];
		int[] min=getMinalongLine(pxs,sX1,sY1,sX2,sY2,W);
		double[] nrml={(double)sX1,(double)sY1,(double)min[1],(double)min[2]};
		//IJ.write(nrml[0]+":"+nrml[1]+":"+nrml[2]+":"+nrml[3]);
		nrml=transform90(nrml);
		//IJ.write(sX1+":"+sY1+" - "+sX2+":"+sY2);
		//IJ.write(nrml[0]+":"+nrml[1]+":"+nrml[2]+":"+nrml[3]);
		int[] enrml=expandLine(nrml[0],nrml[1],nrml[2],nrml[3],W,H);
		//IJ.write(enrml[0]+":"+enrml[1]+":"+enrml[2]+":"+enrml[3]);
		int L=(int) Math.abs(enrml[3]-enrml[1]);
		if (Math.abs(enrml[2]-enrml[0])>Math.abs(enrml[3]-enrml[1])) L=(int) Math.abs(enrml[2]-enrml[0]);
		int offset=enrml[1]*W+enrml[0];
		//IJ.write("L:"+L+"W:"+W+"H:"+H);
		double x_step=(double)L/(double)(enrml[2]-enrml[0]);
	 	double y_step=(double)L/(double)(enrml[3]-enrml[1]);
		for (int k=0;k<=L;k++){
			int x=(int)Math.floor(k/x_step);
			int y=(int)Math.floor(k/y_step);
			index=offset+y*W +x;
			//IJ.write(x+":"+y+" - "+index);			
			pxs[index]=0;
		}
	}
	else{
	ws++;
	for (i=0;i<seeds[0];i++){
		for (j=i+1;j<seeds[0];j++){
			int sX1=seeds[(i*2)+1],sY1=seeds[(i*2)+2];
			int sX2=seeds[(j*2)+1],sY2=seeds[(j*2)+2];
			int[] min=getMinalongLine(pxs,sX1,sY1,sX2,sY2,W);
			if (min[0]<0.45*(pxs[sY1*W+sX1]+pxs[sY2*W+sX2])) {
				if (min[0]>ws) ws=min[0];
			
				}
			}
		}
	
	for (i=0;i<H;i++){
		for (j=0;j<W;j++){
			index=i*W+j;
			if (pxs[index]<=ws) pxs[index]=0;	
			}
		}
	}
	return ws;
	}
private int[] expandLine(double x1,double y1,double x2, double y2,int width, int height){
		int[] a={-1,-1,-1,-1};
		if (x1!=x2){
			double[] X=new double[2];
			double[] Y=new double[2];
			if (x1<x2) {X[0] =x1;X[1] =x2; Y[0] =y1;Y[1] =y2; }
			else {X[0] =x2;X[1] =x1; Y[0] =y2;Y[1] =y1;} 
			double slope=(double)(Y[1]-Y[0])/(double)(X[1]-X[0]);
			a[0]=0;
			a[1]=(int)(Y[0]-(X[0]*slope));
			a[2]=width-1;
			a[3]=(int)(Y[1]+(a[2]-X[1])*slope);
			for (int i=1;i<4;i+=2){
				if (a[i]<0){
					a[i]=0;
					a[i-1]=(int)(X[(i-1)/2]-(Y[(i-1)/2]-a[i])/slope);
					}
				else if  (a[i]>(height-1)){
					a[i]=height-1;
					a[i-1]=(int)(X[(i-1)/2]+(a[i]-Y[(i-1)/2])/slope);
					}
				}
			}
		else if(y2!=y1){ 		
			a[0]=(int)x1;
			a[1]=0;
			a[2]=a[0];
			a[3]=height-1;
			}
		else {
			a[0]=0;
			a[1]=0;
			a[2]=0;
			a[3]=0;
			}
	return a;
}
private double[] transform90(double[] ln){
		double[] nln=new double[4];
		//double slope=-(1.0/((ln[3]-ln[1])/(ln[2]-ln[0])));
		nln[0]=ln[2];nln[1]=ln[3];
		nln[2]=ln[2]-(ln[3]-ln[1]);
		nln[3]=ln[3]+ln[2]-ln[0];
		return nln;
	} 

private int[] getMinalongLine(int[]pxs,int sX1, int sY1,int sX2, int sY2, int W){
					int L = (int) Math.abs(sY2-sY1);
					if (Math.abs(sX2-sX1)>Math.abs(sY2-sY1)) L=(int)Math.abs(sX2-sX1);
					double x_step=(double)L/(double)(sX2-sX1);
					double y_step=(double)L/(double)(sY2-sY1);
					int offset=sY1*W+sX1;
					int index=0,min=255,mIndexX=0,mIndexY=0;
					for (int i=0;i<L;i++){
						int x=(int)Math.floor(i/x_step);
						int y=(int)Math.floor(i/y_step);
						index=offset+y*W +x;
						if (pxs[index]<min){
							min=pxs[index];
							mIndexX=sX1+x;
							mIndexY=sY1+y;
						}
					}
					if ((mIndexX==sX1)&&(mIndexY==sY1)) min=-1;
					if ((mIndexX==sX2)&&(mIndexY==sY2)) min=255;
					int[] retvalue={min, mIndexX, mIndexY,L};
	return retvalue;	
	}
public void getCenteredBy1DGaussian(){
	CurveFitter cF;
	double[] coords=getCenter(pxs,boundW,boundH);
	double [] xs=new double[this.boundW];
	double [] ys=new double[this.boundW];
	for (int i=0;i<this.boundW;i++) {
		xs[i]=(double)this.boundX+i;
		ys[i]=(double)pxs[(this.brightestY-this.boundY)*boundW+i];
	}
	cF=new CurveFitter(xs, ys);
	double [] init={0.0,(double)this.brightest,(double)this.brightestX,this.R/5};
	cF.setInitialParameters(init);
	cF.doFit(CurveFitter.GAUSSIAN);
	this.centerX=cF.getParams()[2];
	if (this.centerX<boundX||this.centerX>boundX+boundW-1) this.centerX=this.boundX+coords[0];
	xs=new double[this.boundH];
	ys=new double[this.boundH];
	for (int i=0;i<this.boundH;i++) {
		xs[i]=(double)this.boundY+i;
		ys[i]=(double)pxs[(this.brightestX-this.boundX)+boundW*i];
	}
	cF=new CurveFitter(xs, ys);
	init[2]=(double)this.brightestY;
	cF.setInitialParameters(init);
	cF.doFit(CurveFitter.GAUSSIAN);
	this.centerY=cF.getParams()[2];
	if (this.centerY<boundY||this.centerY>boundY+boundH-1) this.centerY=this.boundY+coords[1];
	this.intcX=(int)Math.round(this.centerX);
	this.intcY=(int)Math.round(this.centerY);
}

public void getCenteredByBrightest(){
	int rad=2;//(int)Math.ceil(Math.sqrt(this.area));
	int Y=0;
	int X=0;
	int index=0;
	//int n=0;
	double[] weightH=new double[this.boundW];
	double[] weightV=new double[this.boundH];
	double sum=0, tmp=0;
	int YlLm=(this.brightestY-this.boundY-rad<0)?0:this.brightestY-this.boundY-rad;
	int XlLm=(this.brightestX-this.boundX-rad<0)?0:this.brightestX-this.boundX-rad;
	int YuLm=(this.brightestY-this.boundY+rad<this.boundH-1)?this.brightestY-this.boundY+rad:this.boundH-1;
	int XuLm=(this.brightestX-this.boundX+rad<this.boundW-1)?this.brightestX-this.boundX+rad:this.boundW-1;
	//IJ.log(rad+":"+YlLm+","+YuLm+" - "+XlLm+","+XuLm);
	for (int i=YlLm;i<=YuLm;i++){
		for (int j=XlLm;j<=XuLm;j++){
			index=i*this.boundW+j;
			weightV[i]+=pxs[index];
			weightH[j]+=pxs[index];
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
public double[] getCenter(int[] pxs, int W, int H){
	
	double[] weightH=new double[W];
	double[] weightV=new double[H];
	double sum=0,tmp=0;
	double[] ret=new double[2];
	for (int i=0;i<H;i++){
		for (int j=0;j<W;j++) {
			int index=i*W+j;
				weightV[i]+=pxs[index];
				weightH[j]+=pxs[index];
				//sum+=pxs[index];
			}
		}
	for (int j=0;j<W;j++) {
		tmp+=weightH[j];
		sum+=j*weightH[j];
	}
	if (tmp==0) {
		ret[0]=0;
	}
	else ret[0]=sum/tmp;
	tmp=0;sum=0;
	for (int i=0;i<H;i++) {
		tmp+=weightV[i];
		sum+=i*weightV[i];
	}
	if (tmp==0) {
		ret[0]=0;
	}
	else ret[1]=sum/tmp;
	return ret;
}
public void getCentered(){
	double[] coords=getCenter(pxs,boundW,boundH);
	centerX=boundX+coords[0];
	centerY=boundY+coords[1];
	intcX=(int)Math.round(centerX);
	intcY=(int)Math.round(centerY);
	return;
}
}
