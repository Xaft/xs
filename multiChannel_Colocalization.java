import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.awt.SystemColor;
import java.awt.image.IndexColorModel;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.io.*;
import ij.io.*;
import ij.IJ.*;
import ij.plugin.filter.*;
import ij.plugin.filter.PlugInFilter;
//import ij.plugin.frame.*;
import ij.text.*;
import java.text.NumberFormat;
import ij.measure.*;
import ij.util.*;
import java.text.NumberFormat;
import java.util.*;
import ij.util.Tools.*;
import ij.plugin.LutLoader;
import prat.Particle;
import prat.filteredParticle;
import prat.ParticleChain;
//import prat.Seeds;
import prat.processAFrame;
import java.util.concurrent.atomic.AtomicInteger;  

public class multiChannel_Colocalization implements PlugInFilter{

	ImagePlus imp;
	String iTitle="";
	String iDir="";
	protected ImageStack stack;
	int stackSize=0;
	int currChannel=0;
	int W;
	int H;
	int[] min16bit;
	int[] max16bit;
	int BLINKING=1;
	int nChannel=0;
	LutLoader ll;
	LUT[] lts;
	int imgType;
	double[] bckgF;
	double[] sensitivity;
	double[] dtlContrast;
	double[] dynamics;
	int[] filterType;
	boolean previewDone;
	boolean particlesDetected;
	boolean silent;
	int nThreads;
	int [][] pMap;
	Particle[] prevParticles;
	processAFrame[] fP;
	ParticleChain[][] ppFrame;
	int cIndex=1;
	//Runtime r = Runtime.getRuntime();
	ImageStack pathStack;
	//ImagePlus prev;
	//ImagePlus binary;
	Calibration cal;
	double voxelXY;
	Roi cell;
	String colocPrms="0;1;0;1000;1000;true";
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		String filename=null, imagename=null;
		String[] prms=null;
		String[] args=arg.split(",");
		
		if (this.imp==null||(args.length!=0&&args[0].equals("load"))){
			
			OpenDialog oD=new OpenDialog("Open .mcc file...","");
			filename=oD.getDirectory()+oD.getFileName();
			//IJ.log(oD.getFileName());
			iTitle=oD.getFileName().split("\\.")[0];
			iDir=oD.getDirectory();
			if (filename==null) return DONE;
			try{
				FileReader tEog=new FileReader(filename);
				int c=0;
				boolean exceed=false;
				StringBuilder oneLine = new StringBuilder(256);
				while ((c=tEog.read())!=10&&c!=-1) oneLine.append((char) c);
				if (c==-1) return DONE;
				imagename=oneLine.toString().trim();
				File f=new File(imagename);
				if (!f.exists()) {
					imagename=oD.getDirectory()+imagename.substring(imagename.lastIndexOf("\\")+1,imagename.length()).trim();
					f=new File(imagename);
					if (!f.exists()){
						IJ.showMessage("The image file "+imagename+" is missing");
						tEog.close();
						return DONE;
					}
				}
				Opener o=new Opener();
				//IJ.log(imagename);
				this.imp= o.openImage(imagename);
				while (this.imp==null){}
				this.imp.show();
				W=this.imp.getWidth();
				H=this.imp.getHeight();
				while (this.imp==null){}
				if (this.imp.getBitDepth()!=8&&this.imp.getBitDepth()!=16){
					IJ.showMessage("The image:"+imagename+" has to be one of the following: 8bit or 16bit");
					return DONE;
				}
				
				nChannel=this.imp.getNChannels();
				//IJ.log(nChannel+"");
				prms=new String[nChannel+1];
				for (int i=0;i<nChannel;i++){
					oneLine = new StringBuilder(256);
					while  ((c=tEog.read())!=10&&c!=-1) oneLine.append((char) c);
					if (c==-1) return DONE;
					prms[i]=oneLine.toString().trim();
				}
				oneLine = new StringBuilder(256);
				while  ((c=tEog.read())!=10&&c!=-1) oneLine.append((char) c);
				if (c==-1) return DONE;
				try{
					int n=Integer.parseInt(oneLine.toString().trim());
					int[] xs=new int[n];
					int[] ys=new int[n];
						for (int j=0;j<n;j++){
							oneLine = new StringBuilder(256);
							while  ((c=tEog.read())!=10&&c!=-1) oneLine.append((char) c);
							if (c==-1) return DONE;
							String[] tmp=oneLine.toString().split(";");
							xs[j]=Integer.parseInt(tmp[0].trim());
							ys[j]=Integer.parseInt(tmp[1].trim());
							if (!exceed&&(xs[j]>W||ys[j]>H)) exceed=true;
						}
					cell=new PolygonRoi(xs,ys,n,Roi.POLYGON);
					if (exceed) cell.setLocation(0.0,0.0);
					oneLine = new StringBuilder(256);
					while  ((c=tEog.read())!=10&&c!=-1) oneLine.append((char) c);
				}
				catch (Throwable t){}
				if (c!=-1) colocPrms=oneLine.toString().trim();
				if (args.length>1&&args[1].equals("silent")) silent=true;
			}
			catch (FileNotFoundException fne) {
				IJ.showMessage("File not found!" + fne.getMessage());
				return DONE;
			} 
			catch (IOException ioe) {
				IJ.showMessage("I/O error: " + ioe.getMessage());
				return DONE;
			}
		}
		if (this.imp==null) {			
			IJ.error("You must load an image first");            
			return DONE;
		}
		if ((nChannel=this.imp.getNChannels())<2){
			IJ.error("The image must have at least two channels");            
			return DONE;
		}
		
		iTitle=this.imp.getTitle();
		cal=this.imp.getCalibration();
		voxelXY=cal.pixelWidth;
		if (cal.getUnit().equals("micron")||cal.getUnit().equals("um")) voxelXY*=1000;//nm
		//IJ.log(voxelXY+" nm");
		stack = this.imp.getStack();
		stackSize=stack.getSize()/nChannel;
		ppFrame=new ParticleChain[nChannel][stackSize];
		W=stack.getWidth();
		H=stack.getHeight();
		imgType=this.imp.getBitDepth();
		lts=new LUT[nChannel+1];
		particlesDetected=false;
		previewDone=false;
		nThreads=Runtime.getRuntime().availableProcessors();
	
		bckgF=new double[nChannel];
		dynamics=new double[nChannel];
		sensitivity=new double[nChannel];
		dtlContrast=new double[nChannel];
		filterType=new int[nChannel];
		fP=new processAFrame[nChannel];
	
		for (int i=0;i<nChannel;i++){
			String[] tmp=null;
			if (prms==null) tmp="0;1;0.9;1;0.4;0".split(";");
			else tmp=prms[i].split(";");
			//IJ.log(tmp[1]+tmp[2]+tmp[3]);
			bckgF[i]=Double.parseDouble(tmp[1].trim());
			dynamics[i]=Double.parseDouble(tmp[2].trim());
			sensitivity[i]=Double.parseDouble(tmp[3].trim());
			dtlContrast[i]=Double.parseDouble(tmp[4].trim());
			filterType[i]=Integer.parseInt(tmp[5].trim());
			fP[i]=new processAFrame(imgType, W,H,bckgF[i], dynamics[i], sensitivity[i], dtlContrast[i], filterType[i]);
			
		}
		
		this.imp.setSlice(1);
		return NO_IMAGE_REQUIRED+DOES_8G+DOES_16;
	}
	
	public void run(ImageProcessor ip){
			
			ImagePlus Path2D =null;
			if (imgType==8) Path2D = NewImage.createByteImage("", W, H,1, NewImage.FILL_BLACK);
			else if (imgType==16) Path2D = NewImage.createShortImage("", W, H,1, NewImage.FILL_BLACK);
			ImageProcessor cp2D=Path2D.getProcessor();
			for (int i=stackSize;i>0;i--) stack.addSlice("extra",cp2D,i*nChannel);
			channelWindow pW=new channelWindow(imp);
			currChannel=0;
			
		}
	public static void startAndJoin(Thread[] threads, int nThreads) {  
		for (int ithread = 0; ithread < nThreads; ++ithread)  {  
			threads[ithread].setPriority(Thread.NORM_PRIORITY);  
			threads[ithread].start();  
		}  
		try {     
			for (int ithread = 0; ithread < nThreads; ++ithread) threads[ithread].join();  
		} catch (InterruptedException ie){  
			throw new RuntimeException(ie);  
		}  
	}  
	
	
	
	
	


	private class channelWindow extends StackWindow implements ActionListener, AdjustmentListener, MouseListener, MouseWheelListener, KeyListener, ItemListener, WindowListener{
		/*private class selectChannel extends Frame implements ActionListener{
			Button OK;
			Button canc;
			Choice ChannelChoser;
			Dimension D;
			private selectChannel(){
				super("Select additional channel");
				D=new Dimension(0,0);
				setLayout(new GridLayout(1,3));
				ChannelChoser = new Choice();
				int[] images=WindowManager.getIDList();
				for (int i=0;i<images.length;i++){
					ImagePlus tIP=WindowManager.getImage(images[i]);
					int ssize=tIP.getStackSize();
					if ((!tIP.isHyperStack())&&(tIP.getWidth()==W)&&(tIP.getHeight()==H)&&((ssize==1)||(ssize==slices)||(ssize==stackSize))) ChannelChoser.add(tIP.getTitle()); 
				}
				add(ChannelChoser);
				OK=new Button ("OK");
				OK.addActionListener(this);
				add(OK);
				canc=new Button ("Cancel");
				canc.addActionListener(this);
				add(canc);
				pack();
				setVisible(true);
			}
			public synchronized void actionPerformed(ActionEvent e) {
				Object eS=e.getSource();
				if (eS==OK){
					changeImage(ChannelChoser.getSelectedItem());
					dispose();
				}
				if (eS==canc){
					dispose();
				}
			}

		}*/
		private class particleDetector extends Frame implements ActionListener{
			Button butPrev;
			Button butDone;
			Button butCancel;
			TextField bckgF_field;
			TextField dynamics_field;
			TextField sensitivity_field;
			TextField dtlContrast_field;
			Choice channelChooser;
			Choice filterChooser;
			Checkbox prevShow;
			
			
			private particleDetector(){
				super("Particle detection");
				butDetect.setEnabled(false);
				cimp.setMode(IJ.GRAYSCALE);
				cimp.updateAndDraw();
				setLayout(new GridBagLayout());
				GridBagConstraints c = new GridBagConstraints();
				c.insets = new Insets(2,2,2,2);
				c.fill = GridBagConstraints.HORIZONTAL;
				c.gridwidth = 4;
				c.gridx = 0;
				c.gridy = 0;
				channelChooser=new Choice();
				for (int i=0;i<nChannel;i++) channelChooser.add("Channel "+(i+1));
				channelChooser.addItemListener(new ItemListener(){
					@Override
					public void itemStateChanged(ItemEvent e)
						{
						readDialog();

						currChannel=channelChooser.getSelectedIndex();
						
						//weightField.setText(weightF[currChannel]+"");
						bckgF_field.setText(bckgF[currChannel]+"");
						dynamics_field.setText(dynamics[currChannel]+"");
						sensitivity_field.setText(sensitivity[currChannel]+"");
						dtlContrast_field.setText(dtlContrast[currChannel]+"");
						filterChooser.select(filterType[currChannel]);
						imp.setSlice((imp.getCurrentSlice()-1)/nChannel+currChannel*stackSize+1);
						int currSlice=imp.getCurrentSlice() ;
						showPreview=false;
						drawPreview(prevParticles,currSlice,W,H);
						}
				
					}
				);
				add(channelChooser,c);
				c.gridwidth = 1;
				c.gridy=1;
			
				prevShow=new Checkbox("",false);
				prevShow.addItemListener(new ItemListener(){
					@Override
					public void itemStateChanged(ItemEvent e){
						//IJ.log("aaaaa"+showPreview+"-"+previewDone);
						showPreview=!showPreview;
						int currSlice=imp.getCurrentSlice() ;
						if (previewDone) drawPreview(prevParticles,currSlice,W,H);
					}
				});
				add(prevShow,c);
				c.gridwidth = 3;
				c.gridx = 1;
				butPrev = new Button("Preview");
				butPrev.addActionListener(this);
				add(butPrev,c);
			
				c.gridx=0;
				c.gridy=2;
				c.gridwidth=3;
				add(new Label("Background filter size:"),c);
				c.gridx=3;
				c.gridwidth=1;
				bckgF_field=new TextField(bckgF[0]+"");
				add(bckgF_field,c);
				c.gridx=0;
				c.gridy=3;
				c.gridwidth=3;
				add(new Label("Dynamics:"),c);
				c.gridx=3;
				c.gridwidth=1;
				dynamics_field=new TextField(dynamics[0]+"");
				add(dynamics_field,c);
				c.gridx=0;
				c.gridy=4;
				c.gridwidth=3;
				add(new Label("Sensitivity:"),c);
				c.gridx=3;
				c.gridwidth=1;
				sensitivity_field=new TextField(sensitivity[0]+"");
				add(sensitivity_field,c);
				c.gridx=0;
				c.gridy=5;
				c.gridwidth=3;
				add(new Label("Contrast:"),c);
				c.gridx=3;
				c.gridwidth=1;
				dtlContrast_field=new TextField(dtlContrast[0]+"");
				add(dtlContrast_field,c);
				c.gridx=0;
				c.gridy=6;
				c.gridwidth=2;
				add(new Label("Filter:"),c);
				c.gridx=2;
				c.gridwidth=2;
				filterChooser=new Choice();
				filterChooser.add("none");
				filterChooser.add("DoG");
				filterChooser.add("sqrt");
				filterChooser.select(filterType[0]);
				filterChooser.addItemListener(new ItemListener(){
					@Override
					public void itemStateChanged(ItemEvent e)
						{
						filterType[currChannel]=filterChooser.getSelectedIndex();
						}
				
					}
				);
				add(filterChooser,c);
				c.gridx=0;
				c.gridy=7;
				c.gridwidth=2;
				butDone=new Button("GO!");
				butDone.addActionListener(this);
				add(butDone,c);
				c.gridx=2;
				butCancel=new Button("Cancel");
				butCancel.addActionListener(this);
				add(butCancel,c);
				pack();
				setVisible(true);
			}
			private void readDialog(){
				bckgF[currChannel]=Double.parseDouble(bckgF_field.getText());
				if (bckgF[currChannel]>W/2) bckgF[currChannel]=(int)W/2;
				dynamics[currChannel]=Double.parseDouble(dynamics_field.getText());
				sensitivity[currChannel]=Double.parseDouble(sensitivity_field.getText());
				dtlContrast[currChannel]=Double.parseDouble(dtlContrast_field.getText());
			}
			public synchronized void actionPerformed(ActionEvent e) {
				readDialog();
				if (e.getSource() == butPrev) {
					int currSlice=imp.getCurrentSlice() ;
					fP[currChannel].reFeedParams(bckgF[currChannel], dynamics[currChannel],sensitivity[currChannel],dtlContrast[currChannel],filterType[currChannel]);
					prevParticles = fP[currChannel].getAllParticlesonaFrame(stack.getProcessor(currSlice));
					prevShow.setState(true);
					showPreview=true;
					drawPreview(prevParticles,currSlice,W,H);
					IJ.freeMemory();
				}
				if (e.getSource() == butDone) {
					cimp.setMode(IJ.COLOR);
					cimp.updateAndDraw();
					currChannel=0;
					detectParticles();
					butDetect.setEnabled(true);
					dispose();
				}
				if (e.getSource() == butCancel) {
					cimp.setMode(IJ.COLOR);
					cimp.updateAndDraw();
					currChannel=0;
					butDetect.setEnabled(true);
					dispose();
				}
			}
		}
		Button butShowP;
		Button butPstat;
		Button butCToggler;
		Button butSave;
		Button butExport;
		Button butDetect;
		Button butQuickStat;
		Checkbox[] chBox;
		Checkbox remDupl;
		Label[] chLabel;
		Label sBar1;
		Label sBar2;
		Label sBar3;
		Choice ch1,ch2,ch3;
		TextField distField, maskField;
		ImageCanvas ic;
		CompositeImage cimp;
		ScrollbarWithLabel c_cmp,s_cmp;
		GeneralPath[][] pointpFrame;
		GeneralPath[][] particlestoShow;
		GeneralPath[][] ghoststoShow;
		Color pathColor;
		Color selectedColor;
		Color pointColor;
		Color[] particleColor;
		Color ghostColor;
		Color selectedPColor;
		BasicStroke stroke;
		int currSlice;
		int max;
		int slices;
		int clickRange;
		int minTrackLength;
		int maxTrackLength;
		int mTCnt;
		int currTrack;
		int[][] selectedBoundaries=new int[2][2];
		int[][] undoBoundaries=new int[2][2];
		byte selBoundCnt=0;
		boolean showParticles;
		boolean showTracks;
		boolean particlesDrawn;
		boolean playing;
		boolean commenting;
		boolean removeDuplicates;
		boolean showPreview;
		String[] export;
		
		particleDetector pD;
		Thread playThread;
		
		
		private channelWindow(ImagePlus _cimp) {
			
        	super(_cimp);
			String[] colocPrmsSplit=colocPrms.split(";");
			imp.setOpenAsHyperStack(true); 
			imp.updateAndDraw();
			pack();
			this.cimp=(CompositeImage)_cimp;
			this.cimp.setRoi(cell);
			
			//cimp.setLuts(lts);
			cimp.setMode(IJ.COLOR);
			ic=this.getCanvas();
			ic.addMouseListener(this);
			ic.addKeyListener(this);
			ll=new LutLoader();
		
			slices=imp.getStack().getSize()/nChannel;
			pointpFrame=new GeneralPath[nChannel][slices];
			particlestoShow=new GeneralPath[nChannel][slices];
			ghoststoShow=new GeneralPath[nChannel][slices];
			
			for (int i=0;i<slices;i++){
				for (int j=0;j<nChannel;j++){
					pointpFrame[j][i]=new GeneralPath();
					particlestoShow[j][i]=new GeneralPath();
					ghoststoShow[j][i]=new GeneralPath();
				}
			}	
			currTrack=-1;
			showTracks=true;
			showParticles=false;
			particlesDrawn=false;
			playing=false;
			commenting=false;
			currSlice=1;
			imp.setSlice((currSlice-1)*nChannel+currChannel+1);
			max=W*H;
			clickRange=4;
			pathColor=Color.CYAN;
			pointColor=Color.RED;
			selectedColor=Color.YELLOW;
			particleColor=new Color[3];
			particleColor[0]=Color.GREEN;
			particleColor[1]=Color.MAGENTA;
			particleColor[2]=Color.CYAN;
			ghostColor=Color.MAGENTA;
			selectedPColor=Color.PINK;
			stroke=new BasicStroke(0.5f);
			chBox=new Checkbox[nChannel];
			ImageLayout il=(ImageLayout)getLayout();
			int ncomps=getComponentCount();
			GridBagLayout gbl=new GridBagLayout(); 
			setLayout(gbl); 
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.HORIZONTAL;
			c.gridwidth = 2;
			c.gridx =0;
			c.insets = new Insets(0,0,0,0);
			int i=0;
			for (i=0;i<nChannel;i++){
				c.gridy =i;
				chBox[i]=new Checkbox((i+1)+". channel",false);
				chBox[i].addItemListener(this);
				add(chBox[i],c);
				stack.getProcessor(i+1).setLut(lts[i]);
			}
			chBox[0].setState(true);
			c.gridy =i;
			//allBox=new Checkbox("All channels",false);
			//allBox.addItemListener(this);
			//add(allBox,c);
			
			c.gridy = i;
			c.gridwidth = 1;
			butShowP = new Button("Show particles");
			butShowP.setEnabled(particlesDetected);
        	butShowP.addActionListener(this);
			gbl.setConstraints(butShowP,c);
			add(butShowP,c);
			c.gridx = 1;
			butCToggler = new Button("Merge");
        	butCToggler.addActionListener(this);
			gbl.setConstraints(butCToggler,c);
			add(butCToggler,c);
			c.gridy = i+1;
			c.gridx = 0;
			add(new Label("Ref. channel"),c);
			ch1=new Choice();
			for (int j=0;j<nChannel;j++) ch1.add((j+1)+".");
			ch1.select(Integer.parseInt(colocPrmsSplit[0]));
			c.gridx=1;
			add(ch1,c);
			c.gridy = i+2;
			c.gridx = 0;
			add(new Label("Test channel"),c);
			ch2=new Choice();
			for (int j=0;j<nChannel;j++) ch2.add((j+1)+".");
			ch2.select(Integer.parseInt(colocPrmsSplit[1]));
			c.gridx=1;
			add(ch2,c);
			if (nChannel>2) {
				c.gridy = i+3;
				c.gridx = 0;
				add(new Label("Mask channel"),c);
				ch3=new Choice();
				for (int j=0;j<nChannel+1;j++) ch3.add((j)+".");
				ch3.select(Integer.parseInt(colocPrmsSplit[2]));
				c.gridx=1;
				add(ch3,c);
				c.gridy = i+4;
				c.gridx = 0;
				add(new Label("Mask dist (nm)"),c);
				c.gridx=1;
				maskField=new TextField(colocPrmsSplit[4]);
				add(maskField,c);
			}
			c.gridy = i+5;
			c.gridx = 0;
			add(new Label("Max. dist (nm)"),c);
			c.gridx=1;
			distField=new TextField(colocPrmsSplit[3]);
			add(distField,c);
			c.gridy=i+6;
			c.gridx=0;
			c.gridwidth = 2;
			remDupl=new Checkbox("Remove duplicates", colocPrmsSplit[5].trim().equals("true"));
			add(remDupl,c);
			c.gridy=i+7;
			butPstat = new Button("Test colocalization");
			butPstat.setEnabled(particlesDetected);
        	butPstat.addActionListener(this);
			add(butPstat,c);
			c.gridy=i+8;
			c.gridwidth = 1;
			butSave = new Button("Save");
        	butSave.addActionListener(this);
			gbl.setConstraints(butSave,c);
			add(butSave,c);
			c.gridx = 1;
			butExport = new Button("Export CSV");
			butExport.setEnabled(false);
        	butExport.addActionListener(this);
			gbl.setConstraints(butExport,c);
			add(butExport,c);
			c.gridy=i+9;
			c.gridwidth = 2;
			c.gridx = 0;
			butDetect = new Button("Detect particles");
        	butDetect.addActionListener(this);
			gbl.setConstraints(butDetect,c);
			add(butDetect,c);
			c.gridy++;
			c.gridwidth = 2;
			c.gridx = 0;
			butQuickStat = new Button("Quick statistics");
        	butQuickStat.addActionListener(this);
			add(butQuickStat,c);
			c.gridx = 2;
			c.insets = new Insets(5,5,5,5);
			c.gridy=0;
			c.gridheight=16;
			gbl.setConstraints(getComponent(0),c);
			c.gridheight=1;
			c.gridy = 16;
			c_cmp=(ScrollbarWithLabel)getComponent(1);
			gbl.setConstraints(c_cmp,c);
			c_cmp.addAdjustmentListener(this);
			if (slices>1) {
				c.gridy = 17;
				s_cmp=(ScrollbarWithLabel)getComponent(2);
				gbl.setConstraints(s_cmp,c);
				s_cmp.addAdjustmentListener(this);
			}
			/*for (int i=2;i<ncomps;i++){
				c.gridy = 9+i;
				//c.gridx = i;
				gbl.setConstraints(getComponent(i),c);
			}*/
			c.gridy=17;
			c.gridx = 0;
			c.gridwidth = 1;
			sBar1=new Label();
			add(sBar1,c);
			c.gridx = 1;
			sBar2=new Label();
			add(sBar2,c);
			c.gridx = 2;
			sBar3=new Label();
			add(sBar3,c);
			pack();
			//ic.zoomOut(0,0);
			if (silent) {
				detectParticles();
				runner();
			}
			else if (!particlesDetected) {
				pD=new particleDetector();
			}
			
        }
		void drawPreview(Particle[] Particles,int frame, int W, int H){
			//binary=new ImagePlus();
			Overlay ol=new Overlay();
			if (!showPreview) {
				imp.setOverlay(ol);
				return;
			}
			int clor=0;
			GeneralPath simpleParts=new GeneralPath();
			GeneralPath mergedParts=new GeneralPath();
			GeneralPath ghostParts=new GeneralPath();
			Color simpleColor=Color.GREEN;
			Color mergedColor=Color.YELLOW;
			Color ghostColor=Color.MAGENTA;
			BasicStroke stroke=new BasicStroke(1f);
			for (int i=0;i<Particles.length-1;i++){
				if (Particles[i]!=null){
				
					if (Particles[i].merged) {clor=0xffff00; mergedParts.append(Particles[i].shpe, false); }
					else {clor=0x00ff00; simpleParts.append(Particles[i].shpe, false); }
					
				}
			}
			
			
			Roi roi = new ShapeRoi(simpleParts);
			roi.setStrokeColor(simpleColor);
			roi.setStroke(stroke);
			ol.add(roi);
			roi = new ShapeRoi(mergedParts);
			roi.setStrokeColor(mergedColor);
			roi.setStroke(stroke);
			ol.add(roi);
			roi = new ShapeRoi(ghostParts);
			roi.setStrokeColor(ghostColor);
			roi.setStroke(stroke);
			ol.add(roi);
			imp.setOverlay(ol);
			previewDone=true;
		}
		public void detectParticles(){
			int max=W*H;
			particlesDetected=false;
			final Thread[] threads = new Thread[nThreads];
			final AtomicInteger af=new AtomicInteger(0);
			IJ.showProgress(0);
			IJ.showStatus("Detecting particles...");
			long now = System.currentTimeMillis();
			for (int i=0;i<nChannel;i++) fP[i].reFeedParams(bckgF[i], dynamics[i], sensitivity[i], dtlContrast[i],filterType[currChannel],processAFrame.GAUSSIAN1D);
			for (int ithread = 0; ithread < nThreads; ithread++) { 
				threads[ithread] = new Thread() {     
					public synchronized void run() {  
						for (int f= af.getAndIncrement();f<stackSize*nChannel;f=af.getAndIncrement()){
							int frame=f/nChannel+1;
							
							int channel=f%nChannel;
							
								
							
								Particle[] Particles = fP[channel].getAllParticlesonaFrame(stack.getProcessor(f+1));
								
								fP[channel].reset();
								int i=0;
								while (Particles[i]==null) i++;
								ppFrame[channel][frame-1]=new ParticleChain(Particles[i],1);
								
								for (i=1;i<Particles.length;i++){ 
									if (Particles[i]!=null) {
										ppFrame[channel][frame-1].linkParticle(Particles[i]);
									
									}
								}
	
							//IJ.showProgress((double)((f))/(stackSize*nChannel));
						}
					}
				};
			}
			
			startAndJoin(threads, nThreads); 
			long now2 = System.currentTimeMillis();
			/*for (int f=0;f<stackSize*nChannel;f++) {
				int frame=f/nChannel+1;
				drawFrame(W,H,f+1);
			}*/
			for (int i=0;i<nChannel;i++) fP[i].close();
			System.gc();
			butShowP.setEnabled(true);
			butPstat.setEnabled(true);
			particlesDetected=true;
		}
		private void runner(){
			double maskD=0.0;
			if (maskField!=null) maskD=Double.parseDouble(maskField.getText().trim());
			removeDuplicates=remDupl.getState();
			getParticleDistances(Double.parseDouble(distField.getText().trim()), maskD);
		}
		public synchronized void actionPerformed(ActionEvent e) {
			Object eS=e.getSource();
			if (eS==butDetect){
				particleDetector pD=new particleDetector();
			}
			if (eS==butShowP){
				if (!this.particlesDrawn) drawParticles();
				this.showParticles=this.showParticles?false:true;
				if (this.showParticles) butShowP.setLabel("Hide particles");
				else butShowP.setLabel("Show particles");
				this.updateTracksCanvas();
			}
			if (eS==butPstat){
				runner();
			}
			if (eS==butCToggler){
				int m=imp.getCompositeMode();
				if (m==IJ.COLOR) {
					butCToggler.setLabel("Separate");
					boolean[] active= cimp.getActiveChannels();
					for (int i=0;i<nChannel;i++){
						active[i]=chBox[i].getState();
					}
					cimp.setMode(IJ.COMPOSITE);
				}
				else if (m==IJ.COMPOSITE){
					butCToggler.setLabel("Merge");
					cimp.setMode(IJ.COLOR);
				}
			}
			if (eS==butSave) save();
			if (eS==butExport) exportCSV();
			if (eS==butQuickStat) quickStatistics();
		}
		public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
			if (e.getSource() == c_cmp) {

				currChannel=c_cmp.getValue()-1;

				imp.setSlice((currSlice-1)*nChannel+currChannel+1);
				this.updateTracksCanvas();
			}
			if (e.getSource() == s_cmp) {
	
				currSlice=s_cmp.getValue()-1;
				imp.setSlice((currSlice-1)*nChannel+currChannel+1);
				this.updateTracksCanvas();
			}
		}
		
		public void itemStateChanged(ItemEvent e) {
			//IJ.log(e.getSource()+"");
			
				if (showParticles) updateTracksCanvas();
			
   
		
		}
		private void save(){
			save("");
		}
		private void save(String nme){
			String filename, imagename;
			if (nme.equals("")) {
				SaveDialog sd=new SaveDialog("Save file...",iTitle,".mcc");
				filename=sd.getDirectory()+sd.getFileName();
				imagename=filename.substring(0,filename.lastIndexOf(".")==-1?filename.length()-1:filename.lastIndexOf("."))+".tif";
			}
			else {
				String basenme=nme.substring(0,nme.lastIndexOf(".")==-1?nme.length()-1:nme.lastIndexOf("."));
				filename=basenme+".mcc";
				imagename=basenme+".tif";
			}
			try{
				PrintWriter output = new PrintWriter(new FileWriter(filename));
				output.println(imagename);
				for (int i=0;i<nChannel;i++){
					output.println((i)+";"+bckgF[i]+";"+dynamics[i]+";"+sensitivity[i]+";"+dtlContrast[i]+";"+filterType[currChannel]);
				}
				cell=this.cimp.getRoi();
				Polygon p;
				if (cell!=null&&(p=cell.getPolygon())!=null) {
					output.println(p.npoints+"");
					for (int j=0;j<p.npoints;j++){
						output.println(p.xpoints[j]+";"+p.ypoints[j]);
					}
				}
				String t=ch1.getSelectedIndex()+";"+ch2.getSelectedIndex()+";";
				if (ch3!=null) t+=ch3.getSelectedIndex();
				else t+="0";
				t+=";"+distField.getText().trim()+";";
				if (ch3!=null) t+=maskField.getText().trim();
				else t+="1000";
				t+=";"+remDupl.getState();
				output.println(t);
				output.close();
				imp.killRoi();
				ImagePlus nw=imp.duplicate();
				imp.setRoi(cell);
				nw.setTitle(imp.getTitle());
				nw.setOverlay(null);
				nw.getStack().deleteSlice(nChannel+1);
				FileSaver svr=new FileSaver(nw);
				svr.saveAsTiffStack(imagename);
				nw.close();
			}
			catch (FileNotFoundException fne) {
				IJ.showMessage("File not found!" + fne.getMessage());
			} 

			catch (IOException ioe) {
				IJ.showMessage("I/O error: " + ioe.getMessage());
			}
		}
		private void quickStatistics(){
			int counts=0;
			cell=this.cimp.getRoi();
			Particle cPart=null;
			double area=W*H*voxelXY*voxelXY;
			try{
				area=getPolygonArea(cell.getPolygon());
			}
			catch (Throwable t){IJ.log("Oops, we could not get a polygon");}
			
			ResultsTable res=ResultsTable.getResultsTable();
			for (int j=0;j<nChannel;j++){				
				if (cell==null) counts=ppFrame[j][currSlice-1].boundD;
				else {	
					for (int i=0;i<ppFrame[j][currSlice-1].boundD-1;i++){
						cPart=ppFrame[j][currSlice-1].element[i];
						if (cell.contains(cPart.intcX, cPart.intcY)) counts++;
					}
				}
				res.incrementCounter();
				res.addValue("Exp",iTitle);
				res.addValue("Area",area);
				res.addValue("Channel",(j+1));
				res.addValue("Count", counts);
				counts=0;
			}
			res.show("Quick statistics");	
		}
		private double getPolygonArea(Polygon P){
			double sum=0.0;
			for (int i = 0; i < P.npoints-1; i++) sum+= (P.xpoints[i] * P.ypoints[i+1]) - (P.ypoints[i] * P.xpoints[i+1]);
			sum+= (P.xpoints[P.npoints-1] * P.ypoints[0]) - (P.ypoints[P.npoints-1] * P.xpoints[0]);
			sum=voxelXY*voxelXY*Math.abs(0.5 * sum);

			return sum;
		}
		public void updateTracksCanvas(){
			Overlay ol=new Overlay();
			Roi roi= new PointRoi(0,0);
			//int clor=0;
			boolean[] shw=new boolean[nChannel];
			if (this.showParticles) {
				/*if (allBox.getState()){
					for (int i=0;i<nChannel;i++) shw[i]=true;
				}
				else{*/
					for (int i=0;i<nChannel;i++)  {
						shw[i]=chBox[i].getState();
						//IJ.log(i+" , "+ chBox[i].getState());
						}
				//}
				for (int i=0;i<nChannel;i++){
					if (shw[i]){
						roi = new ShapeRoi(particlestoShow[i][currSlice-1]);
						roi.setStrokeColor(particleColor[i]);
						roi.setStroke(stroke);
						ol.add(roi);
						/*roi = new ShapeRoi(ghoststoShow[i][currSlice-1]);
						roi.setStrokeColor(ghostColor);
						roi.setStroke(stroke);
						ol.add(roi);*/
						roi = new ShapeRoi(pointpFrame[i][currSlice-1]);
						roi.setStrokeColor(pointColor);
						roi.setStroke(stroke);
						ol.add(roi);
						//clor++;
					}
				}

			}
			imp.setOverlay(ol);
		}
		private void drawParticles(){
			IJ.showStatus("Drawing particles...");
			Particle cPart;
			for (int j=0;j<nChannel;j++){
				
			  for (int k=0;k<slices;k++){
			
				for (int i=0;i<ppFrame[j][k].boundD-1;i++){
					
					cPart=ppFrame[j][k].element[i];
					//float r=(float)Math.sqrt(cPart.area/Math.PI);
					particlestoShow[j][k].append(cPart.shpe,false);
					pointpFrame[j][k].moveTo(cPart.centerX,cPart.centerY);
					pointpFrame[j][k].lineTo(cPart.centerX,cPart.centerY);
				}
			  }
			}
			this.particlesDrawn=true;
		}
		private ParticleChain[] resizePCArray(ParticleChain[] aray, int nSize){
			ParticleChain[] tmp=new ParticleChain[nSize];
			System.arraycopy(aray,0,tmp,0,aray.length>nSize?nSize:aray.length);
			return tmp;
			
		}
		private int getClosest(Particle cPart, int[] pMap, ParticleChain pcs, double maxD){
			int closest=-1;
			double dist=maxD;
			int maxxD=(int) (dist)+1;
			
			Particle tPart;
			for (int m=(cPart.intcY-maxxD<0?0:cPart.intcY-maxxD);m<=(cPart.intcY+maxxD>=H?H-1:cPart.intcY+maxxD);m++){
						for (int n=(cPart.intcX-maxxD<0?0:cPart.intcX-maxxD);n<=(cPart.intcX+maxxD>=W?W-1:cPart.intcX+maxxD);n++){
							int offset=m*W+n;
							if (offset<max&&pMap[offset]>0){
								tPart=pcs.element[pMap[offset]-1];
								double tdist;
								if ((tdist=Math.sqrt(Math.pow(cPart.centerX-tPart.centerX,2)+Math.pow(cPart.centerY-tPart.centerY,2)))<=dist) {
									dist=tdist;
									closest=pMap[offset]-1;
								}
							}
						}
					}
			return closest;
		}
		private void getParticleDistances(double maxD, double maskD){
			int max=W*H;
			maxD/=voxelXY;
			maskD/=voxelXY;
			int[] pMap=new int[max];
			int[] tMap=new int[max];
			int[] mMap=new int[max];
			Particle cPart;
			double minD=maxD;
			
			int ch=ch1.getSelectedIndex();
			int och=ch2.getSelectedIndex();
			int mch=-1;
			boolean useMask=false;
			if (ch3!=null&&(mch=ch3.getSelectedIndex()-1)>-1) useMask=true;
			cell=this.cimp.getRoi();
			if (useMask) {
				for (int i=0;i<ppFrame[mch][currSlice-1].boundD-1;i++){
					cPart=ppFrame[mch][currSlice-1].element[i];
					if (cell==null||cell.contains(cPart.intcX, cPart.intcY)) mMap[cPart.intcX+W*cPart.intcY]=i+1;
				}
			}
			for (int i=0;i<ppFrame[och][currSlice-1].boundD-1;i++){
				cPart=ppFrame[och][currSlice-1].element[i];
				if (cell==null||cell.contains(cPart.intcX, cPart.intcY)) {
					pMap[cPart.intcX+W*cPart.intcY]=i+1;
				}
			}
			int[] tMapforMC=new int[max];
		
			if (removeDuplicates){
				for (int i=0;i<ppFrame[ch][currSlice-1].boundD-1;i++){
					cPart=ppFrame[ch][currSlice-1].element[i];
					if (cell==null||cell.contains(cPart.intcX, cPart.intcY)) {
						if (!useMask||getClosest(cPart, mMap, ppFrame[mch][currSlice-1],maskD)>-1) tMap[cPart.intcX+W*cPart.intcY]=i+1;
					}
				}
				System.arraycopy(tMap,0,tMapforMC,0,max);
			}
			
			ParticleChain[] pairs=new ParticleChain[ppFrame[ch][currSlice-1].boundD-1];
			export=new String[ppFrame[ch][currSlice-1].boundD];
			for (int i=0;i<ppFrame[ch][currSlice-1].boundD-1;i++){
				cPart=ppFrame[ch][currSlice-1].element[i];
				if ((cell==null||cell.contains(cPart.intcX, cPart.intcY))&&(!useMask||getClosest(cPart, mMap, ppFrame[mch][currSlice-1],maskD)>-1)) pairs[i]=new ParticleChain(cPart, currSlice);

			}
			boolean changed=false;
			int cnt=0;
			do {
				changed=false;
				for (int i=0;i<pairs.length;i++){
					if (pairs[i]!=null&&!pairs[i].classified){
						
						int closest=getClosest(pairs[i].element[0], pMap, ppFrame[och][currSlice-1],maxD);
						if (closest>-1) {
							pairs[i].linkParticle(ppFrame[och][currSlice-1].element[closest]);
							pairs[i].calculateLength();
							pairs[i].classified=true;
							if (removeDuplicates){
								
								if (getClosest(pairs[i].element[1], tMap, ppFrame[ch][currSlice-1], maxD)==i) {
									tMap[pairs[i].element[0].intcX+W*pairs[i].element[0].intcY]=0;
									pMap[pairs[i].element[1].intcX+W*pairs[i].element[1].intcY]=0;
								}
								else {
									pairs[i]=new ParticleChain(pairs[i].element[0],currSlice);
									changed=true;
								}
							}
						}
						else pairs[i].classified=true;
					}
				}
				cnt++;
			} while(removeDuplicates&&changed&&cnt<50);
			if (cnt==50) IJ.log("Max number of iterations reached");
			ImageProcessor cp2D=stack.getProcessor(currSlice*(nChannel+1));	
			cp2D.setValue(0.0);
			cp2D.fill();
			if (imgType==16){
				short[] cpxs=(short[]) cp2D.getPixels();
				
				double factor=65535/(maxD-minD);
				
				for (int i=0;i<pairs.length;i++){
					//IJ.log(i+"");
					short c=0;
					int s=(int)maxD+1;
					if (pairs[i]==null||pairs[i].boundD==1) c=1;
					else {
						c=(short)(65535-(int)((pairs[i].getLength()-minD)*factor));
						s=(int)(pairs[i].getLength()-minD)==0?1:(int)(pairs[i].getLength()-minD);
						int ew=pairs[i].element[1].getEdgeW();
						for (int m=0;m<pairs[i].element[1].boundH;m++){
							for (int n=0;n<pairs[i].element[1].boundW;n++){
								if (pairs[i].element[1].edges[m*ew+n]) cpxs[(pairs[i].element[1].boundY+m)*W+pairs[i].element[1].boundX+n]=c;
							}
						}
						cpxs[(pairs[i].element[1].intcY)*W+pairs[i].element[1].intcX]=(short)65535;
					}
				}
			}
			
			//int index=1;
			ResultsTable res=new ResultsTable();
			res.incrementCounter();
			double td=0.0, tx=0.0, ty=0.0;
			int ti=0, n=0;
			export[0]="#;area;max;bckg;integral;X;Y;distance;closest_X;closest_Y;closest_density";
				for (int j=0;j<pairs.length;j++){
					if (pairs[j]!=null){
						n++;
						res.addValue("Channel",(ch+1));
						res.addValue("area",pairs[j].element[0].area);
						res.addValue("max",pairs[j].element[0].brightest);
						res.addValue("bckg",pairs[j].element[0].bckg);
						res.addValue("integral",pairs[j].element[0].weight);
						res.addValue("X",pairs[j].element[0].centerX*voxelXY);
						res.addValue("Y",pairs[j].element[0].centerY*voxelXY);
						td=-1.0;tx=-1.0;ty=-1.0;
						ti=-1;
						if (pairs[j].boundD>1) {
							td=pairs[j].getLength()*voxelXY;
							tx=pairs[j].element[1].centerX*voxelXY;
							ty=pairs[j].element[1].centerY*voxelXY;
							ti=pairs[j].element[1].weight;
							}
						res.addValue("closest",td);
						res.addValue("closest_X",tx);
						res.addValue("closest_Y",ty);
						res.addValue("closest_int",ti);
						export[n]=j+";"+pairs[j].element[0].area+";"+pairs[j].element[0].brightest+";"+pairs[j].element[0].bckg+";"+pairs[j].element[0].weight+";"+(pairs[j].element[0].centerX*voxelXY)+";"+(pairs[j].element[0].centerY*voxelXY)+";";
						export[n]+=td+";"+tx+";"+ty+";"+ti;
						
						res.incrementCounter();
					}
				}

			res.show("Observed");
			
			this.cimp.updateAndDraw();
			this.cimp.setMode(CompositeImage.COMPOSITE);
			n=0;
			for (int i=0;i<ppFrame[och][currSlice-1].boundD-1;i++) if (cell==null||cell.contains(ppFrame[och][currSlice-1].element[i].intcX, ppFrame[och][currSlice-1].element[i].intcY)) n++;
			//IJ.log("n"+n);
			monteCarlo(100, n, tMapforMC,mMap, maxD, maskD, och, ch, mch);
		}
		public void monteCarlo(int rep, int n, int[] tMapforMC, int[]mMap, double maxD, double maskD, int och, int ch, int mch){
			boolean useMask=false;
			if (ch3!=null&&(mch=ch3.getSelectedIndex()-1)>-1) useMask=true;
			int maxxD=(int)maxD+1;
			double [][] res=new double[rep][ppFrame[ch][currSlice-1].boundD-1];
			int max=W*H;
			Particle tPart;
			cell=this.cimp.getRoi();
			ParticleChain[] pairs=new ParticleChain[ppFrame[ch][currSlice-1].boundD-1];
			for (int i=0;i<rep;i++){
				int [] tMap =new int[max];
				if (removeDuplicates) System.arraycopy(tMapforMC,0,tMap,0,max);
				
			
				for (int j=0;j<ppFrame[ch][currSlice-1].boundD-1;j++){
					Particle cPart=ppFrame[ch][currSlice-1].element[j];
					if ((cell==null||cell.contains(cPart.intcX, cPart.intcY))&&(!useMask||getClosest(cPart, mMap, ppFrame[mch][currSlice-1],maskD)>-1)) pairs[j]=new ParticleChain(cPart, currSlice);
				}
				
				
				ParticleChain pc=new ParticleChain(currSlice-1);
				Random rg=new Random();
				double [][] rc=new double[n][2];
				
				int[] pMap=new int[max];
				int j=0;
				boolean reroll=false;
				try{
				while(j<n){
					reroll=false;
					rc[j][0]=(W-2)*rg.nextDouble()+1;
					rc[j][1]=(H-2)*rg.nextDouble()+1;
					if (cell!=null&&!cell.contains((int)rc[j][0],(int)rc[j][1])) reroll=true;
					if (!reroll){
						for (int k=(rc[j][1]-1<0?0:(int)rc[j][1]-1);k<(rc[j][1]+2>H?H:rc[j][1]+2);k++){
							for (int l=(rc[j][0]-1<0?0:(int)rc[j][0]-1);l<(rc[j][0]+2>W?W:rc[j][0]+2);l++){
								if (pMap[k*W+l]!=0) reroll=true;
							}
						}
					}
					if (!reroll) {
						pc.linkParticle(new Particle(rc[j][0],rc[j][1],0,0,0,0,0));
						pMap[pc.element[j].intcY*W+pc.element[j].intcX]=j+1;
						j++;
					}
					//IJ.log("MC - "+j);
				}
				}
				catch (ArrayIndexOutOfBoundsException e) {IJ.log(e.getLocalizedMessage()+", "+j);}
				boolean changed=false;
				int cnt=0;
				
				do {
					changed=false;
					for (j=0;j<ppFrame[ch][currSlice-1].boundD-1;j++){
						if (pairs[j]!=null&&!pairs[j].classified){
							int closest=getClosest(pairs[j].element[0],  pMap, pc,maxD);
							//if (i==0&&cnt==0) IJ.log("#"+j);
							if (closest>-1) {
								pairs[j].linkParticle(pc.element[closest]);
								pairs[j].calculateLength();
								res[i][j]=pairs[j].getLength()*voxelXY;
								if (removeDuplicates){
									if (getClosest(pairs[j].element[1], tMap, ppFrame[ch][currSlice-1], maxD)==j) {
										tMap[pairs[j].element[0].intcX+W*pairs[j].element[0].intcY]=0;
										pMap[pairs[j].element[1].intcX+W*pairs[j].element[1].intcY]=0;
										pairs[j].classified=true;
									}
									else {
										pairs[j]=new ParticleChain(pairs[j].element[0],currSlice);									//if (i==0&&cnt==0) IJ.log(res[i][j]+", "+pc.element[j].intcX+":"+pc.element[j].intcY+", "+getClosest(p.element[1], tMap, pc, maxD));
										res[i][j]=-1.0;
										changed=true;
									}
								}
							}
							else {
								res[i][j]=-1.0;
								pairs[j].classified=true;
							}
						}
					}
					cnt++;
				} while(removeDuplicates&&changed&&cnt<50);
				if (cnt==50) IJ.log("MC_"+i+", max. number of iterations reached");	
			//IJ.log("MC_"+i);
			}
			
			ResultsTable rs=new ResultsTable();
			rs.incrementCounter();
			for (int j=0;j<rep;j++) export[0]+=";"+"MC"+j;
			int ind=1;
			for (int i=0;i<ppFrame[ch][currSlice-1].boundD-1;i++){
				if (pairs[i]!=null){
					for (int j=0;j<rep;j++) {
						rs.addValue((j+1)+"", res[j][i]);
						export[ind]+=";"+res[j][i];
						}
					ind++;
					rs.incrementCounter();
					//IJ.log("MC_"+i+"_2");
				}
			}
			rs.show("MonteCarlo - "+ rep +" repeats");
			butExport.setEnabled(true);
			exportCSV();
			if (silent) dispose();
		}
		public void exportCSV(){
			String filename="";
			if (!silent) {
				SaveDialog sd=new SaveDialog("Export colocalization data...",iTitle,".csv");
				filename=sd.getDirectory()+sd.getFileName();
			}
			else filename=iDir+iTitle+".csv";
			int index=0;
			try{
				PrintWriter output = new PrintWriter(new FileWriter(filename));
				
				while (index<export.length&&export[index]!=null) {
					output.println(export[index]);
					index++;
				}
				output.close();
				save(filename);	
			}
			catch (FileNotFoundException fne) {
				IJ.showMessage("File not found!" + fne.getMessage());
			} 

			catch (IOException ioe) {
				IJ.showMessage("I/O error: " + ioe.getMessage());
			}
		}
		public synchronized void mousePressed(MouseEvent e) {
		}
		public void mouseClicked(MouseEvent e) {
		}
		public void mouseEntered(MouseEvent arg0) {
		}
		public void mouseExited(MouseEvent arg0) {	
		}
		public void mouseReleased(MouseEvent arg0) {			
		}
		public void mouseWheelMoved(MouseWheelEvent e) {
			int notches = e.getWheelRotation();
			currSlice+=notches;
			if (currSlice<1) currSlice=1;
			if (currSlice>slices) currSlice=slices;
			imp.setSlice(currSlice);
			this.updateTracksCanvas();
		}
		public void keyPressed(KeyEvent e) {
			int kC=e.getKeyCode();
		}
		public void keyReleased(KeyEvent e) {
		}
		public void keyTyped(KeyEvent e) {
		}
		public void windowClosing(WindowEvent e) {
			stack.deleteSlice(nChannel+1);
			imp.killRoi();
			ImagePlus nw=imp.duplicate();
			nw.setTitle(imp.getTitle());
			nw.setOverlay(null);
			nw.updateAndDraw();
			nw.show();
			if (pD!=null) pD.dispose();
			imp.close();
		}

		public void windowClosed(WindowEvent e) {
			
		}

		public void windowOpened(WindowEvent e) {
			
		}

		public void windowIconified(WindowEvent e) {
			
		}

		public void windowDeiconified(WindowEvent e) {
			
		}

		public void windowActivated(WindowEvent e) {
			
		}

		public void windowDeactivated(WindowEvent e) {
			
		}

		public void windowGainedFocus(WindowEvent e) {
			
		}

		public void windowLostFocus(WindowEvent e) {
			
		}

		public void windowStateChanged(WindowEvent e) {
			

		}
	}
}