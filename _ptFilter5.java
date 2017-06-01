import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.awt.SystemColor;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.io.*;
import ij.io.*;

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
import prat.Particle;
import prat.ParticleChain;
//import prat.Seeds;
import prat.processAFrame;
import java.util.concurrent.atomic.AtomicInteger;  

public class _ptFilter5 implements PlugInFilter {
	static boolean ALL=true;
	static boolean FILTERED=false;
	ImagePlus imp;
	CompositeImage cimp;
	String iTitle;
	String saveName;
	protected ImageStack stack;
	int stackSize=0;
	int W;
	int H;
	int nChannel;
	int actualChannel;
	int min16bit;
	int max16bit;
	int BLINKING=1;
	static int maxSteps=10000;
	int imgType;
	double bckgF;
	int weightF;
	float SNR=1f;
	double weightFstd;
	double sensitivity;
	int filterType;
	int startSlice;
	int finalSlice;
	double linkDistance;
	boolean invert;
	boolean geom_center;
	boolean mTrack;
	boolean previewDone;
	boolean showPreview;
	boolean ghostDet;
	int nThreads;
	double dynamics, contrast;
	boolean prefShort;
	processAFrame fP;
	int [][] pMap;
	Particle[] prevParticles;
	
	ParticleChain[] tracks=new ParticleChain[150000];
	ParticleChain[] ppFrame;
	int cIndex=1;
	//Runtime r = Runtime.getRuntime();
	ImageStack pathStack;
	GenericDialog ld;
	Checkbox prevShow;
	Calibration cal;
	double voxelXY;
	String configFile = System.getProperty("user.dir")+System.getProperty("file.separator")+"xsPT.conf";
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		if (imp==null) {			
			IJ.error("You must load an image first");            
			return DONE;
		}
		cal=imp.getCalibration();
		voxelXY=cal.pixelWidth;
		if (cal.getUnit().equals("nm")) voxelXY*=0.001;//nm
		nChannel=this.imp.getNChannels();
		if (nChannel>1) this.cimp=(CompositeImage) imp;
		stack = imp.getStack();
		stackSize=stack.getSize()/nChannel;
		ppFrame=new ParticleChain[stackSize];
		W=stack.getWidth();
		H=stack.getHeight();
		imgType=imp.getBitDepth();
		if (imgType==16){
			min16bit=(int)((ShortProcessor)stack.getProcessor(1)).getMin();
			max16bit=(int)((ShortProcessor)stack.getProcessor(1)).getMax();
		}
		else {
			min16bit=0;
			max16bit=0;
		}
		previewDone=false;
		nThreads=Runtime.getRuntime().availableProcessors();
		fP=new processAFrame(imgType, W,H,0, 0.0, 0.0, 0.0, 0); 
		iTitle=imp.getTitle();
		return DOES_8G+DOES_16;
	}
	public void run(ImageProcessor ip){
		particleDetector pD=new particleDetector();
	}
	private void analyseFrames(final particleDetector pD){
		final int max=W*H;
		final Thread[] threads = new Thread[nThreads];
		final AtomicInteger af=new AtomicInteger(startSlice);
		IJ.showProgress(0);
		IJ.showStatus("Detecting particles...");
		long now = System.currentTimeMillis();
		final int cT=geom_center?processAFrame.CoM:processAFrame.GAUSSIAN1D;
		for (int ithread = 0; ithread < nThreads; ithread++) { 
			threads[ithread] = new Thread() {     
				public synchronized void run() {  
					for (int f= af.getAndIncrement();f<=finalSlice;f=af.getAndIncrement()){
						pD.progressBar.setText((f-startSlice)+"/"+(finalSlice-startSlice+1));
						processAFrame tfP=new processAFrame(imgType, W,H, bckgF, dynamics,sensitivity,contrast,filterType, cT);
						Particle[] Particles = tfP.getAllParticlesonaFrame(stack.getProcessor((f-1)*nChannel+actualChannel));
						//tfP.close();
						int i=0;
						while (Particles[i]==null) i++;
						Particles[i].comment=0;
						ppFrame[f-1]=new ParticleChain(Particles[i],1);
					
						for (i=1;i<Particles.length;i++){ 
							if (Particles[i]!=null) {
								Particles[i].comment=ppFrame[f-1].boundD;
								if (Particles[i].bckg==0||(float)Particles[i].brightest/Particles[i].bckg>=SNR) {
								}
								else Particles[i].ghost=true;
								ppFrame[f-1].linkParticle(Particles[i]);
							}
						}
						int ind=1;
						for (i=1;i<=nChannel;i++){
							if (i!=actualChannel){
								float[][] bckgFiltered = tfP.getBckgfiltered(stack.getProcessor((f-1)*nChannel+i));
								for (int p=0;p<ppFrame[f-1].boundD;p++){
									Particle tP=ppFrame[f-1].element[p];
									tP.bckgs[ind]=bckgFiltered[0][tP.brightestX+tP.brightestY*W];
									int intDens=0;
									for (int j=tP.boundY;j<tP.boundY+tP.boundH;j++){
										for (int k=tP.boundX;k<tP.boundX+tP.boundW;k++){
											int index=j*W+k;
											int index2=(j-tP.boundY)*tP.boundW+k-tP.boundX;
											if (tP.pxs[index2]!=0){
												intDens+=(int)bckgFiltered[1][index];
											}
										}
									}
									tP.weights[ind]=intDens;
									//IJ.log(tP.weight+"-"+intDens);
						
								}
								ind++;
							}	
						}
						
						tfP.close();
						
						
					}
				}
			};
		}
		
		startAndJoin(threads, nThreads); 
		long now2 = System.currentTimeMillis();
		IJ.log("No. threads: "+nThreads+", time: "+(now2-now)+" ms");

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
	
	
	
	private class particleDetector extends Frame implements ActionListener, AdjustmentListener{
			Button butPrev;
			Button butDone;
			Button butCancel;
			TextField bckgF_field;
			TextField dynamics_field;
			TextField contrast_field;
			TextField sensitivity_field;
			TextField linkdistance_field;
			Choice channelChooser;
			Choice filterChooser;
			Checkbox prevShow, invBox, geomBox, shortPref, detGhost, trackManual;
			Scrollbar SNRScrollbar;
			TextField SNRField ;
			public Label progressBar;
			
			private particleDetector(){
				super("Particle detection");
				File f = new File(configFile);
				BufferedReader conf=null;
				if(f.exists() && !f.isDirectory()) try{
					//FileReader fr=new FileReader(configFile);
					conf=new BufferedReader(new FileReader(configFile));
					String configLine = conf.readLine();
					if (configLine!=null) {
						String[] tmp=configLine.toString().split(";");
						bckgF=Double.parseDouble(tmp[0].trim());
						SNR=Float.parseFloat(tmp[1].trim());
						dynamics=Double.parseDouble(tmp[2].trim());
						sensitivity=Double.parseDouble(tmp[3].trim());
						linkDistance=Double.parseDouble(tmp[4].trim());
						invert=tmp[5].trim().equals("true");
						geom_center=tmp[6].trim().equals("true");
						prefShort=tmp[7].trim().equals("true");
						ghostDet=tmp[8].trim().equals("true");
						mTrack=tmp[9].trim().equals("true");
						filterType=Integer.parseInt(tmp[10].trim());
						contrast=Double.parseDouble(tmp[11].trim());
						//step=Integer.parseInt(tmp[0].trim());
					}
					
				}
				catch (IOException e) {}
				finally {
					 if (conf != null) {
						try{
							conf.close();
						}
						catch (IOException e) {
							IJ.showMessage("Caught IOException: " 
							+ e.getMessage());
						}
					}
				}
				if (nChannel>1){
					cimp.setMode(IJ.GRAYSCALE);
					cimp.updateAndDraw();
				}
				setLayout(new GridBagLayout());
				GridBagConstraints c = new GridBagConstraints();
				c.insets = new Insets(2,2,2,2);
				c.fill = GridBagConstraints.HORIZONTAL;
				c.gridwidth = 1;
				c.gridx = 0;
				c.gridy = 0;
				prevShow=new Checkbox("",false);
				prevShow.addItemListener(new ItemListener(){
					@Override
					public void itemStateChanged(ItemEvent e){
						//IJ.log("aaaaa"+showPreview+"-"+previewDone);
						showPreview=!showPreview;
						int currSlice=imp.getCurrentSlice() ;
						if (previewDone) drawPreview(prevParticles);
					}
				});
				add(prevShow,c);
				c.gridwidth = 3;
				c.gridx = 1;
				butPrev = new Button("Preview");
				butPrev.addActionListener(this);
				add(butPrev,c);
			
				c.gridx=0;
				c.gridy++;
				c.gridwidth=4;
				add(new Label("Background filter size:"),c);
				c.gridx=4;
				c.gridwidth=1;
				bckgF_field=new TextField(bckgF+"");
				add(bckgF_field,c);
				c.gridx=0;
				c.gridy++;
				c.gridwidth=4;
				add(new Label("Dynamics:"),c);
				c.gridx=4;
				c.gridwidth=1;
				dynamics_field=new TextField(dynamics+"");
				add(dynamics_field,c);
				c.gridx=0;
				c.gridy++;
				c.gridwidth=4;
				add(new Label("Cut-off freq.:"),c);
				c.gridx=4;
				c.gridwidth=1;
				contrast_field=new TextField(contrast+"");
				add(contrast_field,c);
				c.gridx=0;
				c.gridy++;
				c.gridwidth=4;
				add(new Label("Sensitivity:"),c);
				c.gridx=4;
				c.gridwidth=1;
				sensitivity_field=new TextField(sensitivity+"");
				add(sensitivity_field,c);
				c.gridx=0;
				c.gridy++;
				SNRScrollbar= new Scrollbar(Scrollbar.HORIZONTAL, (int)(SNR*100), 1, 1, 1000);
				SNRScrollbar.addAdjustmentListener(this);
				SNRScrollbar.setUnitIncrement(1); 
				SNRScrollbar.setBlockIncrement(10);
				c.gridwidth = 1;
				add(new Label("S/N"),c);
				c.gridwidth = 3;
				c.gridx = 1;
				add(SNRScrollbar,c);
				c.gridx = 4;
				SNRField = new TextField(SNR+"");
				SNRField.addActionListener(this);
				add(SNRField,c);
				c.gridx=0;
				c.gridy++;
				c.gridwidth=3;
				add(new Label("Filter:"),c);
				c.gridx=3;
				c.gridwidth=2;
				filterChooser=new Choice();
				filterChooser.add("none");
				filterChooser.add("DoG");
				filterChooser.add("sqrt");
				filterChooser.select(filterType);
				filterChooser.addItemListener(new ItemListener(){
					@Override
					public void itemStateChanged(ItemEvent e)
						{
						filterType=filterChooser.getSelectedIndex();
						}
				
					}
				);
				add(filterChooser,c);
				c.gridx=0;c.gridy++;c.gridwidth=5;
				invBox=new Checkbox("Bright background",invert);
				add (invBox,c);
				c.gridy++;
				geomBox=new Checkbox("Geometric center",geom_center);
				add (geomBox,c);
				c.gridx=0;c.gridy++;c.gridwidth=4;
				add(new Label("Max. displacement:"),c);
				c.gridx=4;c.gridwidth=1;
				linkdistance_field = new TextField(linkDistance+"");
				add(linkdistance_field,c);
				c.gridx=0;c.gridy++;c.gridwidth=5;
				shortPref=new Checkbox("Prefer short steps",prefShort);
				add (shortPref,c);
				c.gridy++;
				detGhost=new Checkbox("Ghost detection",ghostDet);
				add (detGhost,c);
				c.gridy++;
				trackManual=new Checkbox("Manual tracking",mTrack);
				add (trackManual,c);
				c.gridx=0;c.gridy++;c.gridwidth=2;
				butDone=new Button("GO!");
				butDone.addActionListener(this);
				add(butDone,c);
				c.gridx=2;
				butCancel=new Button("Cancel");
				butCancel.addActionListener(this);
				add(butCancel,c);
				c.gridx=0;c.gridy++;
				add(new Label("Progress:"),c);
				c.gridx=2;c.gridwidth=3;
				progressBar=new Label("");
				add(progressBar,c);
				pack();
				setVisible(true);
			}
			private void readDialog(){
				bckgF=Double.parseDouble(bckgF_field.getText());
				dynamics=Double.parseDouble(dynamics_field.getText());
				contrast=Double.parseDouble(contrast_field.getText());
				sensitivity=Double.parseDouble(sensitivity_field.getText());
				SNR=Float.parseFloat(SNRField.getText());
				linkDistance=Double.parseDouble(linkdistance_field.getText());
				invert=invBox.getState();
				geom_center=geomBox.getState();
				prefShort=shortPref.getState();
				ghostDet=detGhost.getState();
				mTrack=trackManual.getState();
				checkRanges();
			}
			private void checkRanges(){
				if (bckgF<0) bckgF=5;
				if (bckgF>=W/20) bckgF=W/20;
				if (dynamics<0.5) dynamics=0.5;
				if (dynamics>=1.0) dynamics=1.0;
				if (contrast<0.05) contrast=0.05;
				if (contrast>=1.0) contrast=1.0;
				if (sensitivity<0.1) sensitivity=0.1;
				if (sensitivity>=1.0) sensitivity=1.0;
				
				if (startSlice<1) startSlice=1;
				if (finalSlice<1) finalSlice=stackSize;
				if (startSlice>stackSize) startSlice=1;
				if (finalSlice>stackSize) finalSlice=stackSize;
			}
			private void saveParams(){
				try{
					PrintWriter output = new PrintWriter(new FileWriter(configFile));	
					String out=bckgF+";"+SNR+";"+dynamics+";"+sensitivity+";"+linkDistance+";"+invert+";"+geom_center+";"+prefShort+";"+ghostDet+";"+mTrack+";"+filterType+";"+contrast;
					output.println(out);
					output.close();	
				}
				catch (IOException e) {}
			}
			public synchronized void actionPerformed(ActionEvent e) {
				readDialog();
				if (e.getSource() == butPrev) {
					int currSlice=imp.getCurrentSlice() ;
					fP.reFeedParams(bckgF, dynamics,sensitivity,contrast,filterType);
					prevParticles = fP.getAllParticlesonaFrame(stack.getProcessor(currSlice));
					prevShow.setState(true);
					showPreview=true;
					drawPreview(prevParticles);
					IJ.freeMemory();
				}
				if (e.getSource() == butDone) {
					if (nChannel>1){
						cimp.setMode(IJ.COLOR);
						cimp.updateAndDraw();
					}
					saveParams();
					actualChannel=imp.getChannel();
					//IJ.log(actualChannel+"");
					analyseFrames(this);
					if (!mTrack) linkEmAll();
					pathWindow pW=new pathWindow(imp);
					pW.drawTracks();
					pW.updateTracksCanvas();
					System.gc();
					dispose();
				}
				if (e.getSource() == butCancel) {
					if (nChannel>1){
						cimp.setMode(IJ.COLOR);
						cimp.updateAndDraw();
					}
					dispose();
				}
			}
			public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
				if (e.getSource() == SNRScrollbar) {
					SNR=(float)SNRScrollbar.getValue()/100;
					SNRField.setText(SNR+"");
					if (previewDone){
						int currSlice=imp.getCurrentSlice() ;
						drawPreview(prevParticles);
					}
				}
				
			}
		}
	private class pathWindow extends StackWindow implements ActionListener, AdjustmentListener, MouseListener, MouseWheelListener, KeyListener{
		Vector aSelectedTracks;
		Vector mSelectedTracks;

		Button butReLink;
		Button butFilter;
		Button butSaveFilterd;
		Button butSaveSelectd;
		Button butSaveALL;
		Button butShowP;
		Button butPstat;
		Button manualTracking;
		Button playStop;
		Button unDo;
		Button butDelCurr;
		Button delLast;
		Button delAll;
		Button loadTracks;
		Button exportXML;
		Button comMode;
		TextField linkD;
		TextField minTL;
		TextField maxTL;
		TextField fpsBox;
		//Checkbox blinkBox;
		Checkbox shortBox;
		Checkbox ghostBox;
		Label sBar1;
		Label sBar2;
		Label sBar3;
		
		ImageCanvas ic;
		ScrollbarWithLabel cmp, c_cmp;
		GeneralPath[] pathpFrame;
		GeneralPath[] manualPathpFrame;
		GeneralPath[] selectedpFrame;
		GeneralPath[] pointpFrame;
		GeneralPath[] particlestoShow;
		GeneralPath[] ghoststoShow;
		GeneralPath[] selectedParticles;
		GeneralPath[][] commentPath;
		Color pathColor;
		Color selectedColor;
		Color pointColor;
		Color particleColor;
		Color ghostColor;
		Color selectedPColor;
		BasicStroke stroke;
		Roi cell;
		ParticleChain[] manualTracks;
		
		int drawnTracks;
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
		//boolean mTrack;
		
		final String[] mTS={"Auto tracking","Manual tracking"};
		final String[] cTS={"Select mode","Comment mode"};
		String[] comments={"+","-","nd","+-","","","","",""};
		Color[] commentColor={Color.GREEN,Color.RED, Color.BLUE, Color.MAGENTA, Color.WHITE,Color.WHITE,Color.WHITE,Color.WHITE,Color.WHITE};
		
		Thread playThread;
		
		private pathWindow(ImagePlus imp) {
        	super(imp);
			if (nChannel>1) {
				imp.setOpenAsHyperStack(true); 
				cimp=(CompositeImage)imp;
				cimp.setMode(IJ.COLOR);
			}
			ic=this.getCanvas();
			ic.addMouseListener(this);
			ic.addKeyListener(this);
			slices=imp.getStack().getSize();
			pathpFrame=new GeneralPath[finalSlice-startSlice+1];
			manualPathpFrame=new GeneralPath[finalSlice-startSlice+1];
			selectedpFrame=new GeneralPath[finalSlice-startSlice+1];
			pointpFrame=new GeneralPath[finalSlice-startSlice+1];
			particlestoShow=new GeneralPath[finalSlice-startSlice+1];
			ghoststoShow=new GeneralPath[finalSlice-startSlice+1];
			selectedParticles=new GeneralPath[finalSlice-startSlice+1];
			commentPath=new GeneralPath[9][slices];
			for (int i=0;i<pointpFrame.length;i++){
				pointpFrame[i]=new GeneralPath();
				pathpFrame[i]=new GeneralPath();
				manualPathpFrame[i]=new GeneralPath();
				selectedpFrame[i]=new GeneralPath();
				particlestoShow[i]=new GeneralPath();
				ghoststoShow[i]=new GeneralPath();
				selectedParticles[i]=new GeneralPath();
				for (int j=0;j<9;j++) commentPath[j][i]=new GeneralPath();
			}
			drawnTracks=0;
			if (!mTrack) drawnTracks=cIndex;
			manualTracks=new ParticleChain[100];
			mTCnt=1;
			currTrack=-1;
			showTracks=true;
			showParticles=false;
			particlesDrawn=false;
			playing=false;
			commenting=false;
			currSlice=1;
			max=W*H;
			clickRange=4;
			pathColor=Color.CYAN;
			pointColor=Color.RED;
			selectedColor=Color.YELLOW;
			particleColor=Color.GREEN;
			ghostColor=Color.MAGENTA;
			selectedPColor=Color.PINK;
			aSelectedTracks=new Vector(0);
			mSelectedTracks=new Vector(0);
			stroke=new BasicStroke(0.4f);
			ImageLayout il=(ImageLayout)getLayout();
			int ncomps=getComponentCount();
			GridBagLayout gbl=new GridBagLayout(); 
			setLayout(gbl); 
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.HORIZONTAL;
			c.gridwidth = 2;
			c.gridx =0;
			c.gridy = 0;
			manualTracking = new Button(mTS[mTrack?1:0]);
        	manualTracking.addActionListener(this);
			gbl.setConstraints(manualTracking,c);
			add(manualTracking,c);
			c.gridwidth = 1;
			c.gridx =0;
			c.gridy = 1;
			c.insets = new Insets(0,0,0,0);
			add(new Label("Max. displacement"),c);
			c.gridx = 1;
			linkD=new TextField(linkDistance+"");
			add(linkD,c);
			c.gridx = 0;
			c.gridy = 2;
			c.gridwidth = 2;
			shortBox=new Checkbox("Prefer short steps",prefShort);
			add(shortBox,c);
			c.gridy = 3;
			/*blinkBox=new Checkbox("Blink detection",((BLINKING==2)?true:false));
			add(blinkBox,c);
			c.gridy = 3;*/
			ghostBox=new Checkbox("Ghost detection",ghostDet);
			add(ghostBox,c);
			c.gridy = 4;
			butReLink = new Button(" Relink ");
        	butReLink.addActionListener(this);
			gbl.setConstraints(butReLink,c);
			add(butReLink,c);
			c.gridwidth = 1;
			c.gridy = 5;
			add(new Label("Min. trajectory"),c);
			c.gridx = 1;
			minTL=new TextField("1");
			add(minTL,c);
			minTrackLength=1;
			c.gridx = 0;
			c.gridy = 6;
			add(new Label("Max. trajectory"),c);
			c.gridx = 1;
			maxTL=new TextField((finalSlice-startSlice+1)+"");
			add(maxTL,c);
			maxTrackLength=finalSlice-startSlice+1;
			c.gridwidth = 2;
			c.gridy = 7;
			c.gridx = 0;
			butFilter = new Button("Filter trajectories");
        	butFilter.addActionListener(this);
			gbl.setConstraints(butFilter,c);
			add(butFilter,c);
			c.gridwidth = 1;
			c.gridy = 8;
			butSaveFilterd = new Button("Save filtered");
        	butSaveFilterd.addActionListener(this);
			gbl.setConstraints(butSaveFilterd,c);
			add(butSaveFilterd,c);
			c.gridx = 1;
			butSaveALL = new Button("Save all");
        	butSaveALL.addActionListener(this);
			gbl.setConstraints(butSaveALL,c);
			add(butSaveALL,c);
			c.gridwidth = 2;
			c.gridy = 9;
			c.gridx = 0;
			butSaveSelectd = new Button("Save selected");
        	butSaveSelectd.addActionListener(this);
			gbl.setConstraints(butSaveSelectd,c);
			add(butSaveSelectd,c);
			c.gridy = 10;
			c.gridwidth = 1;
			butShowP = new Button("Show particles");
        	butShowP.addActionListener(this);
			gbl.setConstraints(butShowP,c);
			add(butShowP,c);
			c.gridx = 1;
			butPstat = new Button("Particle stats");
        	butPstat.addActionListener(this);
			gbl.setConstraints(butPstat,c);
			add(butPstat,c);
			c.gridy = 11;
			c.gridx = 0;
			unDo = new Button("Undo");
        	unDo.addActionListener(this);
			gbl.setConstraints(unDo,c);
			add(unDo,c);
			unDo.setEnabled(false);
			if (!mTrack) unDo.setEnabled(false);
			c.gridx = 1;
			butDelCurr = new Button("Delete");
        	butDelCurr.addActionListener(this);
			gbl.setConstraints(butDelCurr,c);
			add(butDelCurr,c);
			if (!mTrack) butDelCurr.setEnabled(false);
			c.gridy=12;
			c.gridx = 0;
			delAll = new Button("Delete All");
        	delAll.addActionListener(this);
			gbl.setConstraints(delAll,c);
			add(delAll,c);
			c.gridy=13;
			c.gridx=0;
			loadTracks = new Button("Load tracks");
        	loadTracks.addActionListener(this);
			gbl.setConstraints(loadTracks,c);
			add(loadTracks,c);
			c.gridx++;
			exportXML = new Button("XML code");
        	exportXML.addActionListener(this);
			add(exportXML,c);
			c.gridy=14;
			c.gridx = 0;
			comMode = new Button("Select mode");
        	comMode.addActionListener(this);
			gbl.setConstraints(comMode,c);
			add(comMode,c);
			c.gridx = 0;
			if (!mTrack) delAll.setEnabled(false);
			c.gridy = 16;
			fpsBox=new TextField("25");
			add(fpsBox,c);
			c.gridx = 1;
			playStop = new Button("PLAY");
        	playStop.addActionListener(this);
			gbl.setConstraints(playStop,c);
			add(playStop,c);
			c.gridx = 2;
			c.insets = new Insets(5,5,5,5);
			c.gridy=0;
			c.gridheight=16;
			gbl.setConstraints(getComponent(0),c);
			c.gridheight=1;
			c.gridy = 16;
			int tcmp=1;
			if (nChannel>1){
				c_cmp=(ScrollbarWithLabel)getComponent(tcmp);
				gbl.setConstraints(c_cmp,c);
				c.gridy++;
				tcmp++;
			}
			if (slices/nChannel>1) {
				cmp=(ScrollbarWithLabel)getComponent(tcmp);
				gbl.setConstraints(cmp,c);
				cmp.addAdjustmentListener(this);
			}
			/*for (int i=2;i<ncomps;i++){
				c.gridy = 9+i;
				//c.gridx = i;
				gbl.setConstraints(getComponent(i),c);
			}*/
			c.gridy++;
			c.gridx = 0;
			c.gridwidth = 1;
			sBar1=new Label();
			add(sBar1,c);
			c.gridx = 1;
			sBar2=new Label(drawnTracks+" tracks");
			add(sBar2,c);
			c.gridx = 2;
			sBar3=new Label();
			add(sBar3,c);
			pack();
			//ic.zoomOut(0,0);
			
        }
		private void showMsg(String s){
			this.sBar3.setForeground(Color.BLACK);
			this.sBar3.setText(s);
		}
		private void showErr(String s){
			this.sBar3.setForeground(Color.RED);
			this.sBar3.setText(s);
		}		
		public synchronized void actionPerformed(ActionEvent e) {
			Object eS=e.getSource();
            if (eS==butReLink) {
			  if ((!mTrack)||(IJ.showMessageWithCancel("Switch mode...","Do you want to switch to automatic tracink mode?"))){
				mTrack=false;
				manualTracking.setLabel(mTS[0]);
				linkDistance=Double.parseDouble(linkD.getText());
				//BLINKING=blinkBox.getState()?2:1;
				prefShort=shortBox.getState();
				ghostDet=ghostBox.getState();
				
				linkEmAll();
				this.resetTracks();
				this.drawTracks();
				this.sBar2.setText(drawnTracks+" tracks");
				this.updateTracksCanvas();
			  }
            }
			if (eS==manualTracking){
				showTracks=mTrack;
				mTrack=mTrack?false:true;
				unDo.setEnabled(false);
				butDelCurr.setEnabled(false);
				delAll.setEnabled(mTrack);
				manualTracking.setLabel(mTS[mTrack?1:0]);
				for (int i=0;i<slices;i++) selectedpFrame[i]=new GeneralPath();
				if (mTrack) selectTracks(manualTracks, mSelectedTracks);
				else selectTracks(tracks, aSelectedTracks);
				this.updateTracksCanvas();
				
			}
			if (eS==butFilter) {
				aSelectedTracks=new Vector(0);
				this.minTrackLength=Integer.parseInt(minTL.getText());
				this.maxTrackLength=Integer.parseInt(maxTL.getText());
				
				this.resetTracks();
				this.cell=this.imp.getRoi();
				this.filterTracks();
				this.drawTracks();
				this.sBar2.setText(drawnTracks+" tracks");
				this.updateTracksCanvas();
			}
			if (eS==butShowP){
				if (!this.particlesDrawn) drawParticles();
				this.showParticles=this.showParticles?false:true;
				if (this.showParticles) butShowP.setLabel("Hide particles");
				else butShowP.setLabel("Show particles");
				this.updateTracksCanvas();
			}
			if (eS==loadTracks){
				ldTracks();
			}
			if (eS==exportXML) {
				expXML();
			}
			if (eS==butPstat){
				logProperties();
			}
			if (eS==butSaveFilterd) {
				saveResults(1);
			}
			if (eS==butSaveALL) {
				saveResults(0);
			}
			if (eS==butSaveSelectd) {
				saveResults(2);
			}
			if (eS==playStop) {
				final int pause=(1000/Integer.parseInt(fpsBox.getText()));
				final pathWindow pw=this;
				playing=playing?false:true;

				playThread=new Thread(){
					public synchronized void run() { 
						actualChannel=1;
						if (nChannel>1) actualChannel=c_cmp.getValue();
						while(playing){
						long now = System.currentTimeMillis();
						int wt=0;
						currSlice++;
						if (currSlice>slices/nChannel) currSlice=1;
						imp.setSlice((currSlice-1)*nChannel+actualChannel);
						updateTracksCanvas();
						cmp.setValue(currSlice);
						long now2 = System.currentTimeMillis();
						if ((wt=(pause-(int)(now2-now)))>0) IJ.wait(wt);
						}
					}
				};
				playThread.setPriority(Thread.NORM_PRIORITY);
				if (playing) {
					playThread.start();
					playStop.setLabel("STOP");
					}
				else {  
					playThread.stop();
					playStop.setLabel("PLAY");
					}
				//}
			}
			if (eS==unDo){
				switch (selBoundCnt){
					case 0: break;
					case 1: 
						selBoundCnt=0;
						break;
					case 2:
						selectedBoundaries[0][0]=undoBoundaries[0][0];
						selectedBoundaries[0][1]=undoBoundaries[0][1];
						manualTracks[currTrack].shrinkChain(selectedBoundaries[0][0]+1,1);
						selectTracks(manualTracks, mSelectedTracks, manualPathpFrame);
						selBoundCnt=1;
						break;
					case 3:
						selectedBoundaries[0][0]=undoBoundaries[0][0];
						selectedBoundaries[0][1]=undoBoundaries[0][1];
						selectedBoundaries[1][0]=undoBoundaries[1][0];
						selectedBoundaries[1][1]=undoBoundaries[1][1];
						//IJ.log(selectedBoundaries[0][0]+"+"+selectedBoundaries[1][0]);
						manualTracks[currTrack].shrinkChain(selectedBoundaries[0][0]+1,selectedBoundaries[1][0]-selectedBoundaries[0][0]+1);
						selectTracks(manualTracks, mSelectedTracks, manualPathpFrame);
						break;
				}
				unDo.setEnabled(false);
				this.updateTracksCanvas();	
				/*if (selBoundCnt>0){
					if (selBoundCnt==1) selBoundCnt=0;
					else {
						selectedBoundaries[0][0]=undoBoundaries[0][0];
						selectedBoundaries[0][1]=undoBoundaries[0][1];
						selectedBoundaries[1][0]=undoBoundaries[1][0];
						selectedBoundaries[1][1]=undoBoundaries[1][1];
						//IJ.log(selectedBoundaries[0][0]+"+"+selectedBoundaries[1][0]);
						manualTracks[mTCnt].shrinkChain(selectedBoundaries[0][0]+1,selectedBoundaries[1][0]-selectedBoundaries[0][0]+1);
						selectTracks(manualTracks, mSelectedTracks, manualPathpFrame);
					}
					
				}*/
			}
			if (eS==butDelCurr&&currTrack>-1){
				manualTracks[currTrack]=null;
				Integer m=new Integer(currTrack);
				mSelectedTracks.remove(m);
				this.sBar1.setText("");
				this.sBar2.setText(mSelectedTracks.size()+" tracks");
				showMsg(currTrack+" track deleted");
				//this.sBar3.setText(currTrack+" track deleted");
				currTrack=-1;
				resetTracks();
				selectTracks(manualTracks, mSelectedTracks, manualPathpFrame);
				selBoundCnt=0;
				this.updateTracksCanvas();
			}
			/*if (eS==delLast){
				if (mTCnt>1) {
					if (selBoundCnt==0) mTCnt--;
					selBoundCnt=0;
					manualTracks[mTCnt]=null;
					Integer m=new Integer(mTCnt);
					IJ.log(m+"");
					mSelectedTracks.remove(m);
					
					
					selectTracks(manualTracks, mSelectedTracks);
					this.updateTracksCanvas();
					this.sBar1.setText(mTCnt+"/");
				}
			}*/
			if (eS==delAll){
				if (IJ.showMessageWithCancel("Delete ALL","Do you really want to delete ALL the tracks?")){
					selBoundCnt=0;
					for (int i=0;i<mTCnt;i++) manualTracks[i]=null;
					mTCnt=1;
					mSelectedTracks.removeAllElements();
					for (int i=0;i<selectedParticles.length;i++) {
						selectedParticles[i].reset();
						selectedpFrame[i].reset();
						pathpFrame[i].reset();
						}
					this.updateTracksCanvas();
					this.sBar1.setText(0+"/");
				}
			}
			
			if (eS==comMode){
				commenting=commenting?false:true;
				comMode.setLabel(cTS[commenting?1:0]);
				if (commenting) {
					manualTracking.setEnabled(false);
					//butDelCurr.setEnabled(true);
					for (int i=0;i<slices;i++) pathpFrame[i]=selectedpFrame[i];
					resetSelection();
					}
				
				else {
					manualTracking.setEnabled(true);
					//butDelCurr.setEnabled(false);
					if (!mTrack) {
						drawTracks();
						selectTracks(tracks,aSelectedTracks);
					}
					else {
						for (int i=0;i<slices;i++) {selectedpFrame[i]=new GeneralPath();pathpFrame[i]=new GeneralPath();}
						selectTracks(manualTracks, mSelectedTracks);
					}
				}
				this.updateTracksCanvas();
			}
		}
		
		private void ldTracks(){
			mSelectedTracks=new Vector(0);
			mTCnt=1;
			boolean first=true;
			BufferedReader todos=null;
			OpenDialog od = new OpenDialog("Open track data ...","");
			String directory = od.getDirectory();
			String fileName = od.getFileName();
			if (fileName==null) return;
			try {
	        	FileReader fr=new FileReader(directory+fileName);
	        	todos=new BufferedReader(fr);
	        		//int c;
				String sep=";";
				String tmpParticle = todos.readLine();
				
				String[] tmp=tmpParticle.toString().split(sep);
				
				if (tmp.length==1) {
					sep=",";
					tmp=tmpParticle.toString().split(",");
				}
				if (tmp.length>1){
					
					try{Integer.parseInt(tmp[0]);todos.reset();}
					catch (NumberFormatException e){
						
					}
					while ((tmpParticle=todos.readLine())!=null){
						tmp=tmpParticle.toString().split(sep);
						//IJ.log(mTCnt+"-"+tmp[0]);
						if (tmp.length>1&&(!tmp[0].equals("")||!tmp[0].equals("-1"))){
							double cX=(double)(Double.parseDouble(tmp[2].trim()))/voxelXY;
							double cY=(double)(Double.parseDouble(tmp[3].trim()))/voxelXY;
							int z=Integer.parseInt(tmp[1].trim());
							double closestDist=W;
							double tmpDist=0;
							int closest=-1;
							Particle cPart;
							for (int i=1;i<ppFrame[z-1].boundD;i++){
								cPart=ppFrame[z-1].element[i];
								if (Math.abs(cPart.centerX-cX)<=1&&Math.abs(cPart.centerY-cY)<=1){
									tmpDist=Math.sqrt(Math.pow(cPart.centerX-cX,2)+Math.pow(cPart.centerY-cY,2));
									if ((tmpDist<1)&&(tmpDist<closestDist)) {
										closestDist=tmpDist;
										closest=i;
									}
								}
							}
							//IJ.log(closest+"-"+ppFrame[currSlice-1].element[closest].comment);
							if (closest==-1){
								//IJ.log("-1");
								Particle mPart=getParticleAtClick((int)cX,(int)cY);
								ppFrame[z-1].linkParticle(mPart);
								closest=ppFrame[z-1].boundD-1;
							}
							//int a=Integer.parseInt(tmp[5].trim());
							Particle p=ppFrame[z-1].element[closest];
							//p.weight=Integer.parseInt(tmp[6].trim());
							if (first){
								if (mTCnt>=manualTracks.length) {
									manualTracks=resizePCArray(manualTracks,mTCnt*2);
									//IJ.log(mTCnt+":"+manualTracks.length);
								}
								manualTracks[mTCnt]=new ParticleChain(p, z);
								first=false;
							}
							else manualTracks[mTCnt].linkParticle(p);
						}
						else{
							if (manualTracks[mTCnt].boundZ<=slices&&manualTracks[mTCnt].boundZ+manualTracks[mTCnt].boundD-1<=slices) {
								Integer mTI=new Integer(mTCnt);
								mSelectedTracks.add(mTI);
								mTCnt++;
							}
							first=true;
						}
					}
					this.updateTracksCanvas();
				}
			}
			catch (FileNotFoundException e) {
				IJ.showMessage("FileNotFoundException: " 
                       	 + e.getMessage());
			}
			catch (IOException e) {
				IJ.showMessage("Caught IOException: " 
                        + e.getMessage());
			}
			finally {
          		 if (todos != null) {
					try{
						todos.close();
					}
					catch (IOException e) {
						IJ.showMessage("Caught IOException: " 
                        + e.getMessage());
					}
				}
			}
			selectTracks(manualTracks, mSelectedTracks, manualPathpFrame);
			this.sBar1.setText(mSelectedTracks.size()+"/");
			this.sBar2.setText(mSelectedTracks.size()+" tracks");
			showMsg(mSelectedTracks.size()+" tracks loaded");
			//this.sBar3.setText("Tracks loaded");
		}
		public void logProperties(){
			int index=1;
			ResultsTable res=new ResultsTable();
			res.incrementCounter();
			for (int i=0;i<ppFrame.length;i++){
				for (int j=0;j<ppFrame[i].element.length;j++){
					if (!ppFrame[i].element[j].ghost){
						res.addValue("frame",(i+1));
						res.addValue("X",ppFrame[i].element[j].centerX*voxelXY);
						res.addValue("Y",ppFrame[i].element[j].centerY*voxelXY);
						res.addValue("area",ppFrame[i].element[j].area);
						res.addValue("max",ppFrame[i].element[j].brightest);
						for (int k=0;k<nChannel;k++) {
								res.addValue("integral_"+k,ppFrame[i].element[j].weights[k]);
								res.addValue("bckg_"+k,ppFrame[i].element[j].bckgs[k]);
							}
						res.incrementCounter();
					}
				}
			}
			res.show("Results");
		}
		private void expXML(){
			ParticleChain[] _t=null;
			Vector tTS=new Vector(0);
			String output="";
			TextWindow tW=new TextWindow("XML code", output, 1000,500);
			if (mTrack) {
				_t=manualTracks;
				tTS.addAll(mSelectedTracks);
			}
			else {
				_t=tracks;
				if (aSelectedTracks.size()>0) {
					tTS.ensureCapacity(aSelectedTracks.size());
					tTS.addAll(aSelectedTracks);
				}
				else {
					tTS.ensureCapacity(cIndex-1);
					for (int i=1;i<cIndex;i++) if (_t[i].classified) tTS.add(new Integer(i));
				}
			}
			for (int i=0;i<tTS.size();i++){
				Integer index=(Integer)tTS.get(i);
				int clor=0xff0000;
				clor+=_t[index].last().intcX>_t[index].first().intcX?0x00ff00:0;
				clor+=_t[index].last().intcY>_t[index].first().intcY?0x0000ff:0;
				if (clor==0xffffff) clor=0x00ffff;
				tW.append("<path type=\"0\" id=\""+i+"\" clor=\""+(clor)+"\" strt=\""+(_t[index].boundZ-1)+"\" len=\""+_t[index].boundD+"\" mdl=\""+(20)+"\">");
				for (int j=0;j<_t[index].boundD;j++){
					tW.append("<p>"+_t[index].element[j].intcX+","+_t[index].element[j].intcY+"</p>");
				}
				tW.append("</path>");
			}
			for (int i=0;i<tTS.size();i++){
				Integer index=(Integer)tTS.get(i);
				int clor=0x00ff00;
				for (int j=0;j<_t[index].boundD;j++){
					//IJ.log(_t[index].element[j].shpe+"");
					if (_t[index].element[j].shpe instanceof Ellipse2D.Double) {
						tW.append("<ellipse type=\"6\" id=\""+i+"\" clor=\""+(clor)+"\" strt=\""+(_t[index].boundZ-1+j)+"\" len=\""+1+"\" x=\""+_t[index].element[j].intcX+"\" y=\""+_t[index].element[j].intcY+"\" wdth=\""+((int)(_t[index].element[j].R*2))+"\" heght=\""+((int)(_t[index].element[j].R*2))+"\"></ellipse>");
					}
					else {
						Polygon _tP=(Polygon) _t[index].element[j].shpe;
						tW.append("<polygon type=\"5\" id=\""+i+"\" clor=\""+(clor)+"\" strt=\""+(_t[index].boundZ-1+j)+"\" len=\""+1+"\" n=\""+_tP.npoints+"\">");
						for (int k=0;k<_tP.npoints;k++){
							tW.append("<p>"+_tP.xpoints[k]+","+_tP.ypoints[k]+"</p>");
						}
						tW.append("</polygon>");
					}
				}
			}
			
		}
		public void saveResults(int mode){
			String filterMode=new String();
			Vector tracksToSave=new Vector(0);
			if (mTrack){
				filterMode="manually_selected";	
			}
			else {
			switch (mode){
				case 0:
					tracksToSave.ensureCapacity(cIndex-1);
					for (int i=1;i<cIndex;i++) tracksToSave.add(new Integer(i));
					filterMode="ALL";	
				break;
				case 1:
					tracksToSave.ensureCapacity(cIndex-1);
					for (int i=1;i<cIndex;i++) if (tracks[i].classified) tracksToSave.add(new Integer(i));
					filterMode="filtered_"+minTrackLength+".."+maxTrackLength;
				break;
				case 2:
					tracksToSave.ensureCapacity(aSelectedTracks.size());
					tracksToSave.addAll(aSelectedTracks);
					filterMode="selected";	
				break;
			}
			}
			if (saveName==null) saveName=iTitle+"_"+startSlice+".."+finalSlice+"_"+filterMode;
			SaveDialog sd=new SaveDialog("Save file...",saveName,".csv");
			saveName=sd.getFileName();
			String filename=sd.getDirectory()+saveName;
			try{
				PrintWriter output = new PrintWriter(new FileWriter(filename));	
				String out=new String();
				String closer="-1,,,,,,";
				for (int i=1;i<=nChannel;i++) closer+=",,";
				out="no,frame,X(um),Y(um),comment,area,max,integral,bckg";
				for (int i=1;i<=nChannel;i++) if (i!=actualChannel) out+=",integral_"+i+",bckg_"+i;
				output.println(out);
				if (mTrack){
					for (int i=0;i<mSelectedTracks.size();i++){
						Integer index=(Integer)mSelectedTracks.get(i);
						String cmnt= manualTracks[index].getComment();
						for (int j=0;j<manualTracks[index].boundD;j++){
							out=new String();
							out+=(i+1);
							out+=","+(manualTracks[index].boundZ+j);
							out+=",";out+=(manualTracks[index].element[j].centerX*voxelXY);
							out+=",";out+=(manualTracks[index].element[j].centerY*voxelXY);
							out+=",";out+=cmnt;
							out+=",";out+=(manualTracks[index].element[j].area);
							out+=",";out+=(manualTracks[index].element[j].brightest);
							for (int k=0;k<nChannel;k++) {
								out+=",";out+=(manualTracks[index].element[j].weights[k]);
								out+=",";out+=(manualTracks[index].element[j].bckgs[k]);
							}
							output.println(out);
						}
						output.println(closer);
					}
				}
				else{
					for (int i=0;i<tracksToSave.size();i++){
						Integer index=(Integer)tracksToSave.get(i);
						String cmnt= tracks[index].getComment();
							for (int j=0;j<tracks[index].boundD;j++){
								out=new String();
								out+=(i+1);
								out+=","+(tracks[index].boundZ+j);
								out+=",";out+=(tracks[index].element[j].centerX*voxelXY);
								out+=",";out+=(tracks[index].element[j].centerY*voxelXY);
								out+=",";out+=cmnt;
								out+=",";out+=(tracks[index].element[j].area);
								out+=",";out+=(tracks[index].element[j].brightest);
								for (int k=0;k<nChannel;k++) {
									out+=",";out+=(tracks[index].element[j].weights[k]);
									out+=",";out+=(tracks[index].element[j].bckgs[k]);
								}
								output.println(out);
							}
						output.println(closer);			
					}
				}
				output.close();			
			}
		
			catch (FileNotFoundException fne) {
				IJ.showMessage("File not found!" + fne.getMessage());
			} 

			catch (IOException ioe) {
				IJ.showMessage("I/O error: " + ioe.getMessage());
			}
        }
		public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
			if (e.getSource() == cmp || e.getSource() == c_cmp) {
				currSlice=cmp.getValue();
				actualChannel=1;
				if (nChannel>1) actualChannel=c_cmp.getValue();
				imp.setSlice((currSlice-1)*nChannel+actualChannel);
				this.updateTracksCanvas();
				/*ic.setDisplayList(pathpFrame[currSlice-1],pathColor,stroke);
				ic.setDisplayList(pointpFrame[currSlice-1],pointColor,stroke);*/
			}
		}
		public void updateTracksCanvas(){
			Overlay ol=new Overlay();
			Roi roi= new PointRoi(0,0);
			if (commenting) {
				if (mTrack) roi = new ShapeRoi(manualPathpFrame[currSlice-1]);
				else roi = new ShapeRoi(pathpFrame[currSlice-1]);
				roi.setStrokeColor(pathColor);
				roi.setStroke(stroke);
				ol.add(roi);
				
				for (int i=0;i<9;i++){
					//IJ.log((commentPath[i][currSlice-1]!=null)+"");
					if (commentPath[i][currSlice-1]!=null){
						roi = new ShapeRoi(commentPath[i][currSlice-1]);
						roi.setStrokeColor(commentColor[i]);
						roi.setStroke(stroke);
						ol.add(roi);	
					}
				}
				roi = new ShapeRoi(selectedpFrame[currSlice-1]);
				roi.setStrokeColor(selectedColor);
				roi.setStroke(stroke);
				ol.add(roi);
				//IJ.log("commentingDone");
			}
			else {
			 if (this.showTracks){
				roi = new ShapeRoi(pathpFrame[currSlice-1]);
				roi.setStrokeColor(pathColor);
				roi.setStroke(stroke);
				ol.add(roi);
				roi = new ShapeRoi(pointpFrame[currSlice-1]);
				roi.setStrokeColor(pointColor);
				roi.setStroke(stroke);
				ol.add(roi);
			 }
			 if (mTrack){
				roi = new ShapeRoi(manualPathpFrame[currSlice-1]);
				roi.setStrokeColor(pathColor);
				roi.setStroke(stroke);
				ol.add(roi);
				roi = new ShapeRoi(selectedParticles[currSlice-1]);
				roi.setStrokeColor(selectedPColor);
				roi.setStroke(stroke);
				ol.add(roi);
			}
		
			}
			if (this.showParticles) {
				roi = new ShapeRoi(particlestoShow[currSlice-1]);
				roi.setStrokeColor(particleColor);
				roi.setStroke(stroke);
				ol.add(roi);
				roi = new ShapeRoi(ghoststoShow[currSlice-1]);
				roi.setStrokeColor(ghostColor);
				roi.setStroke(stroke);
				ol.add(roi);
			}
			
			if ((this.showTracks)||(mTrack)){
				roi = new ShapeRoi(selectedpFrame[currSlice-1]);
				roi.setStrokeColor(selectedColor);
				roi.setStroke(stroke);
				ol.add(roi);
			 }
			imp.setOverlay(ol);
		}
		
		private void resetTracks(){
			for (int i=0;i<pathpFrame.length;i++){
				manualPathpFrame[i]=new GeneralPath();
				pathpFrame[i]=new GeneralPath();
				pointpFrame[i]=new GeneralPath();
				selectedpFrame[i]=new GeneralPath();
			}
		}
		private void resetSelection(){
			for (int i=0;i<selectedpFrame.length;i++) selectedpFrame[i]=new GeneralPath();
		}
		private void filterTracks(){
			for (int i=1;i<cIndex;i++){
				tracks[i].classified=true;
				if ((tracks[i].boundD<minTrackLength)||(tracks[i].boundD>maxTrackLength)) tracks[i].classified=false;
			}
			if (cell!=null&&cell.isArea()) {
				for (int i=1;i<cIndex;i++){
					if (!cell.contains(tracks[i].first().intcX,tracks[i].first().intcY)&&!cell.contains(tracks[i].last().intcX,tracks[i].last().intcY)) tracks[i].classified=false;
				}	
			}	
		}
		public void drawTracks(){
			IJ.showStatus("Drawing tracks...");
			int currFrame=0;
			drawnTracks=0;
			for (int i=1;i<cIndex;i++){
				if ((tracks[i].classified)){
					drawnTracks++;
					for (int j=0;j<tracks[i].boundD;j++){
						if (tracks[i].boundZ+j<=finalSlice){
							//IJ.write(tracks[i].boundZ+j-startSlice+1+"");
							currFrame=tracks[i].boundZ+j-startSlice;
							for (int k=j;(k>0)&&(k>j-20);k--){
								/*if (tracks[i].element[k].area==-1) cp.setColor(Color.MAGENTA);
								else if (tracks[i].element[k].area<-1) cp.setColor(Color.YELLOW);
								else cp.setColor(Color.CYAN);*/
								pathpFrame[currFrame].moveTo((float)tracks[i].element[k-1].centerX,(float)tracks[i].element[k-1].centerY);
								pathpFrame[currFrame].lineTo((float)tracks[i].element[k].centerX,(float)tracks[i].element[k].centerY);
								//cp.drawLine(tracks[i].element[k-1].intcX,tracks[i].element[k-1].intcY,tracks[i].element[k].intcX,tracks[i].element[k].intcY);
							}
							
							pointpFrame[currFrame].moveTo(tracks[i].element[j].centerX,tracks[i].element[j].centerY);
							pointpFrame[currFrame].lineTo(tracks[i].element[j].centerX,tracks[i].element[j].centerY);
							
						}
					}
				}
				IJ.showProgress(i,cIndex);
			}	
		}
		private void drawParticles(){
			IJ.showStatus("Drawing particles...");
			Particle cPart;
			for (int j=0;j<particlestoShow.length;j++){
				for (int i=0;i<ppFrame[j].boundD;i++){
					cPart=ppFrame[j].element[i];
					if (cPart.weight>weightF) particlestoShow[j].append(cPart.shpe,false);
					else ghoststoShow[j].append(cPart.shpe,false);
				}
			}
			this.particlesDrawn=true;
		}
		private void selectOneTrack(ParticleChain PC, GeneralPath[] selCanvas){
			int currFrame=0;
			//IJ.log(selCanvas+"");
			//IJ.log(PC.boundD+"");
			for (int j=0;j<PC.boundD;j++){
				if (PC.boundZ+j<=finalSlice){
					currFrame=PC.boundZ+j-startSlice;
					for (int k=j;(k>0)&&(k>j-20);k--){
						selCanvas[currFrame].moveTo((float)PC.element[k-1].centerX,(float)PC.element[k-1].centerY);
						selCanvas[currFrame].lineTo((float)PC.element[k].centerX,(float)PC.element[k].centerY);
					}
				}
			}
			//IJ.log("selDone");
		}
		private void selectTracks(ParticleChain[] trcks, Vector selectedTracks){
			selectTracks(trcks, selectedTracks, selectedpFrame);
		}
		private void selectTracks(ParticleChain[] trcks, Vector selectedTracks, GeneralPath[] sFrame){
			int currFrame=0;
			int index;
			Integer tmp;
			for (int i=0;i<selectedpFrame.length;i++){
				sFrame[i]=new GeneralPath();
			}
			for (int i=0;i<selectedTracks.size();i++){
				
				tmp=(Integer)selectedTracks.get(i);
				index=tmp.intValue();
				
				try{
				if (index>0){
				for (int j=0;j<trcks[index].boundD;j++){
					if (trcks[index].boundZ+j<=finalSlice){
						currFrame=trcks[index].boundZ+j-startSlice;
						for (int k=j;(k>0)&&(k>j-20);k--){
							sFrame[currFrame].moveTo((float)trcks[index].element[k-1].centerX,(float)trcks[index].element[k-1].centerY);
							sFrame[currFrame].lineTo((float)trcks[index].element[k].centerX,(float)trcks[index].element[k].centerY);
						}
						
					}
				}
				}
				}
				catch (Exception e){
					IJ.showMessage(index+""+e);
					throw new RuntimeException(e); 
				}
			}
			
			return;
		}
		private void selectParticle (int frame, int pindex){
			Particle cPart=ppFrame[frame].element[pindex];
			selectedParticles[frame].append(cPart.shpe,false);
			return;
		}
		private boolean tryAndConnect (int frame, int pindex){
			int[] from=new int[2];
			int[] to=new int[2];
			boolean preceed=false;
			if (selBoundCnt==1){
				if (frame<selectedBoundaries[0][0]){
					from[0]=frame;
					from[1]=pindex;
					to[0]=selectedBoundaries[0][0];
					to[1]=selectedBoundaries[0][1];
					preceed=true;
				}
				else if (frame>selectedBoundaries[0][0]){
					to[0]=frame;
					to[1]=pindex;
					from[0]=selectedBoundaries[0][0];
					from[1]=selectedBoundaries[0][1];

				}
				else return false;
			}
			else {
				if (frame<selectedBoundaries[0][0]){
					from[0]=frame;
					from[1]=pindex;
					to[0]=selectedBoundaries[0][0];
					to[1]=selectedBoundaries[0][1];
					preceed=true;
				}
				else if (frame>selectedBoundaries[1][0]){
					to[0]=frame;
					to[1]=pindex;
					from[0]=selectedBoundaries[1][0];
					from[1]=selectedBoundaries[1][1];
				}
				else return false;
			}
			//IJ.log(from[1]+"-"+to[1]);
			Particle endPart=ppFrame[to[0]].element[to[1]];
			int range=to[0]-from[0]+1;
			int[] possibleWalks=new int[10*range];
			int minpos=0;
			if (range>2){
				
				double[] pWalkDist =new double[10];
				boolean[] ghostSeen=new boolean[10];
				int pWCnt=1;
				possibleWalks[0]=from[1];
				ghostSeen[0]=false;
				pWalkDist[0]=0.0;
				linkDistance=Double.parseDouble(linkD.getText());
				//IJ.log(range+"r");
				for (int i=0;i<range-1;i++){
					IJ.log(range+" i:"+i);
					int nextSlice=from[0]+i+1;
					int[] tMap=new int[max];
					int j=0;
					for (j=0;j<ppFrame[nextSlice].boundD;j++){
						tMap[ppFrame[nextSlice].element[j].intcX+W*ppFrame[nextSlice].element[j].intcY]=j;
					}
					
					//IJ.log(ppFrame[nextSlice].element[j].intcX+","+ppFrame[nextSlice].element[j].intcY);

					int loopEnd=pWCnt;
					//IJ.log("loop"+pWCnt+" - "+possibleWalks.length);
					boolean canWalk=false;
					
					for (j=0;j<loopEnd;j++){
						if (possibleWalks[j*range]!=-1){
							int newWalk=-1;
							Particle cPart=ppFrame[nextSlice-1].element[possibleWalks[j*range+i]];
							//IJ.log("   - "+cPart.intcX+" - "+cPart.intcY+".");
							for (int m=(int)Math.floor(cPart.intcY-linkDistance);m<=(int)Math.ceil(cPart.intcY+linkDistance);m++){
								for (int n=(int)Math.floor(cPart.intcX-linkDistance);n<=(int)Math.ceil(cPart.intcX+linkDistance);n++){
									int offset=m*W+n;
									int index=0;
									if ((offset>=0)&&(offset<max)&&((index=tMap[offset])>0)) {
										if ((ppFrame[nextSlice].element[index].weight>=weightF)||((ghostDet)&&(!ghostSeen[j]))){
											double dist=Math.sqrt((Math.pow(ppFrame[nextSlice].element[index].centerX-cPart.centerX,2)+Math.pow(ppFrame[nextSlice].element[index].centerY-cPart.centerY,2)));
											if (dist<=linkDistance){
												double dist2=Math.sqrt((Math.pow(ppFrame[nextSlice].element[index].centerX-endPart.centerX,2)+Math.pow(ppFrame[nextSlice].element[index].centerY-endPart.centerY,2)));
												//IJ.log("   - dist:"+dist+":"+(linkDistance*(range-i-2)) );
												if (dist2<=linkDistance*(range-i-2)){
													if (newWalk==-1) {
														possibleWalks[j*range+i+1]=index;
														pWalkDist[j]+=dist;
														if ((ghostDet)&&(ppFrame[nextSlice].element[index].weight<weightF)) ghostSeen[j]=true;
														else ghostSeen[j]=false;
													}
													else {
														if (pWCnt*range>=possibleWalks.length) {
															//IJ.log("increase+");
															int[] tmp=new int[2*pWCnt*range];
															System.arraycopy(possibleWalks, 0, tmp, 0, possibleWalks.length);
															possibleWalks = tmp;
															double[] tmd=new double[2*pWCnt];
															System.arraycopy(pWalkDist, 0, tmd, 0, pWalkDist.length);
															pWalkDist=tmd;
															boolean[] tmb=new boolean[2*pWCnt];
															System.arraycopy(ghostSeen, 0, tmb, 0, ghostSeen.length);
															ghostSeen=tmb;
															//IJ.log(possibleWalks.length+""+pWalkDist.length);
															}
														for (int l=0;l<range;l++) possibleWalks[l+pWCnt*range]=possibleWalks[l+j*range];
														possibleWalks[pWCnt*range+i+1]=index;
														pWalkDist[pWCnt]=pWalkDist[j]+dist;
														if ((ghostDet)&&(ppFrame[nextSlice].element[index].weight<weightF)) ghostSeen[pWCnt]=true;
														else ghostSeen[pWCnt]=false;
														pWCnt++;
														}
													//IJ.log(newWalk+"endj");
													newWalk++;
													canWalk=true;
												}
											}
										}
									}
								}
							}
						
							if (newWalk==-1) possibleWalks[j*range]=-1;
						
						
						}
					}
					
					if (!canWalk) {
						showErr("No path found!");
						return false;
					}
				}
				//IJ.log("Over");
				double mindist=0xffffff;
				
				for (int j=0;j<pWCnt;j++){
					if ((possibleWalks[j*range]!=-1)&&(pWalkDist[j]<mindist)){
						IJ.log("j:"+j+" - "+pWalkDist[j]);
						mindist=pWalkDist[j];
						minpos=j;
					}
				}
			}
			else{
				//if (Math.sqrt((Math.pow(ppFrame[to[0]].element[to[1]].centerX-ppFrame[from[0]].element[from[1]].centerX,2)+Math.pow(ppFrame[to[0]].element[to[1]].centerY-cPart.centerY,2)));)
				possibleWalks[1]=to[1];
			}
			//IJ.log("-"+minpos);
			if (currTrack>=manualTracks.length) manualTracks=resizePCArray(manualTracks, mTCnt*2);
			int tbZ=from[0]+1;
			if (!preceed) tbZ++;
			ParticleChain tmPc=new ParticleChain(ppFrame[from[0]+1].element[possibleWalks[minpos*range+1]], tbZ);
			for (int j=2;j<range;j++){
				//IJ.log("P:"+(from[0]+j)+"-"+possibleWalks[minpos*range+j]);
				tmPc.linkParticle(ppFrame[from[0]+j].element[possibleWalks[minpos*range+j]]);
			}
			//tmPc.linkParticle(endPart);
			if (manualTracks[currTrack]==null) {
				manualTracks[currTrack] = new ParticleChain(ppFrame[from[0]].element[from[1]], from[0]+1); 
				Integer mTI=new Integer(currTrack);
				mSelectedTracks.add(mTI);
			}
			if (!manualTracks[currTrack].mergeChain(tmPc)) IJ.showMessage(currSlice+":"+frame+","+manualTracks[currTrack].boundZ+"+"+manualTracks[currTrack].boundD+"+"+tmPc.boundZ+"-"+tmPc.boundD);
			
			//IJ.log("mT:"+(manualTracks[mTCnt]==null));
			//for (int j=0;j<manualTracks[mTCnt].boundD;j++) IJ.log(manualTracks[mTCnt].element[j].area+"");
			
			//mTCnt++;
			undoBoundaries[0][0]=selectedBoundaries[0][0];
			undoBoundaries[0][1]=selectedBoundaries[0][1];
			undoBoundaries[1][0]=selectedBoundaries[1][0];
			undoBoundaries[1][1]=selectedBoundaries[1][1];
			selectedBoundaries[0][0]=manualTracks[currTrack].boundZ-1;
			selectedBoundaries[0][1]=from[1];
			selectedBoundaries[1][0]=manualTracks[currTrack].boundZ+manualTracks[currTrack].boundD-2;
			selectedBoundaries[1][1]=to[1];
			if (selBoundCnt<3) selBoundCnt++;
			unDo.setEnabled(true);
			butDelCurr.setEnabled(true);
			return true;
		}
		private ParticleChain[] resizePCArray(ParticleChain[] aray, int nSize){
			ParticleChain[] tmp=new ParticleChain[nSize];
			System.arraycopy(aray,0,tmp,0,aray.length>nSize?nSize:aray.length);
			return tmp;
			
		}
		/*private void addWalk(int[] possibleWalks, double[] pWalkDist, int pWCnt, int walk, int cS, int index, int range, double dist){
			
		
		}*/
		private Particle getParticleAtClick(int _mouseX, int _mouseY){
			try{
				int[] currpxs=fP.getPxels(stack.getProcessor(currSlice));
				int brightest=currpxs[_mouseY*W+_mouseX];
				int bX=_mouseX;
				int bY=_mouseY;
				int a=0;
				int index=0;
				for (int m=(_mouseY-1<0)?0:_mouseY-1;m<((_mouseY+2>H)?H:_mouseY+2);m++){
					for (int n=(_mouseX-1<0)?0:_mouseX-1;n<((_mouseX+2>W)?W:_mouseX+2);n++){
						index=m*W+n;
						a++;
						if (currpxs[index]>brightest){
							brightest=currpxs[index];
							bX=n;
							bY=m;
						}
					}
				}
				int bckg=0;
				int N=0;
				int bcF=(int)bckgF*10;
				for (int m=(bY-bcF<0)?0:bY-bcF;m<((bY+bcF+1>H)?H:bY+bcF+1);m++){
					for (int n=(bX-bcF<0)?0:bX-bcF;n<((bX+bcF+1>W)?W:bX+bcF+1);n++){
						N++;
						bckg+=currpxs[m*W+n];
					}
				}
				bckg/=N;
				
				Particle mPart=new Particle(0.0,0.0,a, (bX-2<0)?0:bX-2, (bY-2<0)?0:bY-2,(bX+3>W)?4-bX-3+W:4, (bY+3>H)?4-bY-3+H:4);
				for (int m=0;m<mPart.boundH;m++){
					for (int n=0;n<mPart.boundW;n++){
						index=m*mPart.boundW+n;
						mPart.pxs[index]=currpxs[(m+mPart.boundY)*W+n+mPart.boundX]-bckg;
						if (mPart.pxs[index]<0) mPart.pxs[index]=0;
					}
				}
				//mPart.trim();
				mPart.getShape(false);
				mPart.getCentered();
				mPart.weight=weightF+1;
				mPart.comment=ppFrame[currSlice-1].boundD;
				return mPart;
			}
			catch (Throwable t){
				IJ.log(t+", "+t.getCause()+", "+t.getLocalizedMessage());
				return null;
			}
		}
		public synchronized void mousePressed(MouseEvent e) {
			String tool=IJ.getToolName();
			if ((!tool.equals("hand"))&&(!tool.equals("point"))) return;
			int mouseX = ic.offScreenX((int)e.getX());
			int mouseY = ic.offScreenY((int)e.getY());
			//IJ.log(e.getModifiers()+"-"+e.getMouseModifiersText(e.getModifiers())+":"+mouseX+","+mouseY+":"+ic.offScreenX((int)e.getX()));
			int offset=0;
			int index=0;

			int closest=-1;
			double closestDist=0xffffff;
			double tmpDist=0.0;
			Particle cPart;
			if (mTrack&&!commenting&&(e.getModifiers()&1)==0){
				if (e.getButton()==MouseEvent.BUTTON2) {
					if (selBoundCnt>1) {
						mTCnt++;
						currTrack=mTCnt;
					}
					showMsg(mSelectedTracks.size()+". track finished");
					butDelCurr.setEnabled(false);
					unDo.setEnabled(false);
					//this.sBar3.setText(mSelectedTracks.size()+". track finished");
					selBoundCnt=0;
				}
				else if (e.getButton()==MouseEvent.BUTTON1){
				for (int i=1;i<ppFrame[currSlice-1].boundD;i++){
					cPart=ppFrame[currSlice-1].element[i];
					if (Math.abs(cPart.intcX-mouseX)<=clickRange&&Math.abs(cPart.intcY-mouseY)<=clickRange){
						tmpDist=Math.sqrt(Math.pow(cPart.centerX-mouseX,2)+Math.pow(cPart.centerY-mouseY,2));
						if ((tmpDist<clickRange)&&(tmpDist<closestDist)) {
							closestDist=tmpDist;
							closest=i;
						}
					}
				}
				//IJ.log(closest+"-"+ppFrame[currSlice-1].element[closest].comment);
				if (closest==-1||(e.getModifiers()&InputEvent.CTRL_MASK)==InputEvent.CTRL_MASK){
					//IJ.log("-1");
					Particle mPart=getParticleAtClick(mouseX,mouseY);
					particlestoShow[currSlice-1].append(mPart.shpe,false);
					ppFrame[currSlice-1].linkParticle(mPart);
					closest=ppFrame[currSlice-1].boundD-1;
				}
				showMsg((mSelectedTracks.size()+1)+". track being tracked");
	
				if (selBoundCnt<1) {
					selectedBoundaries[selBoundCnt][0]=currSlice-1;
					selectedBoundaries[selBoundCnt][1]=closest;
					selBoundCnt++;
					this.sBar1.setText((mSelectedTracks.size()+1)+"/");
					
					//this.sBar3.setText((mSelectedTracks.size()+1)+". track being tracked");
					currTrack=mTCnt;
					unDo.setEnabled(false);
				}
				else {
					//currTrack=mTCnt;
					if (tryAndConnect(currSlice-1,closest)) {
						selectTracks(manualTracks, mSelectedTracks, manualPathpFrame);
						this.sBar2.setText(mSelectedTracks.size()+" tracks");
					}
				}
				
				selectParticle(currSlice-1,closest);
				this.updateTracksCanvas();
				}
				
				
				
			}
			else{
			if (mTrack){
				for (int i=0;i<mSelectedTracks.size();i++){
					int ind=(Integer)mSelectedTracks.get(i);
					
					if ((ind>0)&&(currSlice>=manualTracks[ind].boundZ-startSlice+1)&&(currSlice<manualTracks[ind].boundZ+manualTracks[ind].boundD-startSlice+1)){
						cPart=manualTracks[ind].element[currSlice-1-(manualTracks[ind].boundZ-startSlice)];
						tmpDist=Math.sqrt(Math.pow(cPart.centerX-mouseX,2)+Math.pow(cPart.centerY-mouseY,2));
						//IJ.log(tmpDist+"");
						if ((tmpDist<clickRange)&&(tmpDist<closestDist)) {
							closestDist=tmpDist;
							closest=ind;
						}
					}
				}
			}
			else{
			for (int i=1;i<cIndex;i++){
				//IJ.write(startSlice+"");
				if ((tracks[i].boundD>=minTrackLength)&&(tracks[i].boundD<=maxTrackLength)&&(currSlice>=tracks[i].boundZ-startSlice+1)&&(currSlice<tracks[i].boundZ+tracks[i].boundD-startSlice+1)){
					cPart=tracks[i].element[currSlice-1-(tracks[i].boundZ-startSlice)];
					tmpDist=Math.sqrt(Math.pow(cPart.centerX-mouseX,2)+Math.pow(cPart.centerY-mouseY,2));
					if ((tmpDist<clickRange)&&(tmpDist<closestDist)) {
						closestDist=tmpDist;
						closest=i;
					}
					
				}
				
			}
			}
			if (closest!=-1){
				if (e.getButton()==MouseEvent.BUTTON1) {
					if (commenting||(e.getModifiers()&1)==1){
						if ((mTrack)||((!mTrack)&&aSelectedTracks.indexOf(new Integer(closest))!=-1)){
							resetSelection();
							if (mTrack) selectOneTrack(manualTracks[closest], selectedpFrame);
							else selectOneTrack(tracks[closest], selectedpFrame);
							currTrack=closest;
							selectedBoundaries[0][0]=manualTracks[closest].boundZ-1;
							selectedBoundaries[0][1]=manualTracks[closest].element[0].comment;
							selectedBoundaries[1][0]=manualTracks[closest].boundZ+manualTracks[closest].boundD-2;
							selectedBoundaries[1][1]=manualTracks[closest].element[manualTracks[closest].boundD-1].comment;
							selBoundCnt=3;
							unDo.setEnabled(false);
							butDelCurr.setEnabled(true);
							//for (int z=0;z<manualTracks[closest].boundD;z++) IJ.log("  n "+manualTracks[closest].element[z].comment);
							//IJ.log(selectedBoundaries[0][0]+","+selectedBoundaries[1][0]);
							this.sBar1.setText(closest+"/");
							showMsg(closest+". track selected");
							//this.sBar3.setText(closest+". track selected");
						}
					}
					else {
						Integer tmp=new Integer(closest);
						
						if (aSelectedTracks.indexOf(tmp)==-1) {
							aSelectedTracks.add(tmp);
							selectOneTrack(tracks[closest], selectedpFrame);
							showMsg(closest+". track selected");
							this.sBar1.setText(aSelectedTracks.size()+"/");
						}
					}
				}
				else if ((!commenting)&&(e.getButton()==MouseEvent.BUTTON2)) {
					int i=0;
					//IJ.write(maxI+"");
					Integer tmp=new Integer(closest);
					if ((i=aSelectedTracks.indexOf(tmp))!=-1) {
						aSelectedTracks.remove(i);
						selectTracks(tracks, aSelectedTracks);
						this.sBar1.setText(aSelectedTracks.size()+"/");
					}
				}	
				this.updateTracksCanvas();
			}
			}
			
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
			actualChannel=1;
			if (nChannel>1) actualChannel=c_cmp.getValue();
			if (currSlice<1) currSlice=1;
			if (currSlice>slices/nChannel) currSlice=slices/nChannel;
			cmp.setValue(currSlice);
			imp.setSlice((currSlice-1)*nChannel+actualChannel);
			this.updateTracksCanvas();
		}
		public void keyPressed(KeyEvent e) {
			int kC=e.getKeyCode();
			
			if (kC==37){
				//IJ.log(currSlice+"");
				currSlice=(currSlice-1)<1?1:currSlice-1;
				//imp.setSlice(currSlice);
				this.updateTracksCanvas();
				}
			else if (kC==39){
				currSlice=(currSlice+1)>slices?slices:currSlice+1;
				//imp.setSlice(currSlice);
				this.updateTracksCanvas();
				}
			else if (kC==32){
				if (selBoundCnt>1) mTCnt++;
				showMsg((mTCnt-1)+". track finished");
				//this.sBar3.setText((mTCnt-1)+". track finished");
				selBoundCnt=0;
				}
			if ((commenting)&&(kC>96)&&(kC<106)){
				if (mTrack) {
					manualTracks[currTrack].addComment(comments[kC-97]);
					selectOneTrack(manualTracks[currTrack], commentPath[kC-97]);
					}
				else {
					tracks[currTrack].addComment(comments[kC-97]);
					selectOneTrack(tracks[currTrack], commentPath[kC-97]);
					}
				resetSelection();
				this.updateTracksCanvas();
			}
					
		}
		public void keyReleased(KeyEvent e) {
		}
		public void keyTyped(KeyEvent e) {
		}


	}
	
	void linkEmAll() {
		IJ.showStatus("Linking particles...");
		pMap=new int[stackSize][W*H];
		int max=W*H;
		int i=0;
		int j=0;
		if (cIndex>1) {
			for (i=0;i<cIndex;i++) tracks[i]=null;
		}	
		cIndex=1;
		//IJ.write(ppFrame[startSlice-1].boundD+"");
		if (ppFrame[startSlice-1].boundD>tracks.length) extendTracks(2*ppFrame[startSlice-1].boundD);
		for (i=0;i<ppFrame[startSlice-1].boundD;i++) {
			if (!ppFrame[startSlice-1].element[i].ghost){
				tracks[cIndex]=new ParticleChain(ppFrame[startSlice-1].element[i],startSlice);
				pMap[startSlice-1][ppFrame[startSlice-1].element[i].intcX+W*ppFrame[startSlice-1].element[i].intcY]=cIndex;
				cIndex++;
			}
		}
		//IJ.write(cIndex+"");
		for (int f=startSlice;f<finalSlice;f++){
			IJ.showProgress(f-startSlice,finalSlice-startSlice);
			int[] tMap= new int[max];
			
			final ParticleChain[] tPart= new ParticleChain[ppFrame[f].boundD+1];
			
			for (i=0;i<ppFrame[f].boundD;i++){
				tPart[i+1]=new ParticleChain(ppFrame[f].element[i], f+1);
				tMap[ppFrame[f].element[i].intcX+W*ppFrame[f].element[i].intcY]=(i+1);
			}
			int cTrack=0;
			Particle cPart;
			for (i=0;i<max;i++){
				cTrack=pMap[f-1][i];
				if ((cTrack)>0) {
					cPart=tracks[cTrack].last();
					int cOffset=0;
					double score=0.0;
					int iter=1;
					int index=0;
					//do{
						if (cPart.area==-1) iter=2;
						score=0xffffff;
						for (int m=(int)Math.floor(cPart.intcY-iter*linkDistance);m<=(int)Math.ceil(cPart.intcY+iter*linkDistance);m++){
							for (int n=(int)Math.floor(cPart.intcX-iter*linkDistance);n<=(int)Math.ceil(cPart.intcX+iter*linkDistance);n++){
								int offset=m*W+n;
								
								if ((offset>=0)&&(offset<max)&&((index=tMap[offset])>0)) {
									//IJ.write(index+":");
									double dist=Math.sqrt((Math.pow(tPart[index].element[0].centerX-cPart.centerX,2)+Math.pow(tPart[index].element[0].centerY-cPart.centerY,2)));	
									//IJ.write(cPart.intcX+",");
									if (dist<=iter*linkDistance){

										double tscore=-1.0;
										if (!tPart[index].element[0].ghost||(tPart[index].element[0].ghost&&ghostDet)) {
											//tscore=(10/tracks[cTrack].boundD)+Math.pow((tPart[index].element[0].area-cPart.area)/(tPart[index].element[0].area+cPart.area),2)+Math.pow((tPart[index].element[0].weight-cPart.weight)/(tPart[index].element[0].weight+cPart.weight),2)+Math.pow((tPart[index].element[0].brightest-cPart.brightest)/(tPart[index].element[0].brightest+cPart.brightest),2);
											//double linear=tracks[cTrack].getLength()!=0.0?Math.sqrt(Math.pow(tracks[cTrack].element[tracks[cTrack].boundD-1].centerX-tracks[cTrack].element[0].centerX,2)+Math.pow(tracks[cTrack].element[tracks[cTrack].boundD-1].centerY-tracks[cTrack].element[0].centerY,2))/tracks[cTrack].getLength():1.0;
											//IJ.log(cTrack+"-"+linear+","+tracks[cTrack].getLength());
											tscore=1-Math.sqrt((Math.pow(tPart[index].element[0].centerX-tracks[cTrack].element[0].centerX,2)+Math.pow(tPart[index].element[0].centerY-tracks[cTrack].element[0].centerY,2)))/(tracks[cTrack].physLength+dist);
											//tscore=(1-tracks[cTrack].linearity);
											if (prefShort) tscore+=Math.pow(dist/linkDistance,2);
											}
										if (tscore>-1){
											tracks[cTrack].addSniff(index, tscore);
											tPart[index].addSniff(cTrack, tscore);
											
										}
										/*if (tscore<=score){
											score=tscore;
											cOffset=offset;
										}*/
									}
								}
							}
						}
				}
			}
					//while ((iter<BLINKING)&&(cOffset==0));
			//for (int a=1;a<cIndex;a++) tracks[a].arrangeSniffs();
			final Thread[] threads = new Thread[nThreads];
			final AtomicInteger af=new AtomicInteger(0);
			for (int ithread = 0; ithread < nThreads; ithread++) { 
				threads[ithread] = new Thread() {     
					public synchronized void run() {  
						for (int a= af.getAndIncrement();a<cIndex;a=af.getAndIncrement()) tracks[a].arrangeSniffs();
					}
				};
			}
			startAndJoin(threads, nThreads); 
			//for (int a= 0;a<tPart.length;a++) tPart[a].arrangeSniffs();
			final AtomicInteger bf=new AtomicInteger(0);
			for (int ithread = 0; ithread < nThreads; ithread++) { 
				threads[ithread] = new Thread() {     
					public synchronized void run() {  
						for (int a= bf.getAndIncrement();a<=tPart.length;a=bf.getAndIncrement()) tPart[a].arrangeSniffs();
					}
				};
			}
			startAndJoin(threads, nThreads); 
			for (j=1;j<cIndex;j++){
				boolean linked=false;
				int k=0;
				int pIndex;
				cPart=tracks[j].last();
				//IJ.write(j+":"+tracks[j].sniffCnt+"-"+tracks[j].sniffPos[0]);
				if (tracks[j].sniffCnt>0) {

					do{
						pIndex=tracks[j].sniffPos[k];
						if (tPart[pIndex].sniffPos[0]!=j){
							/*if (j==67724){
								for (int q=0;q<tPart[pIndex].sniffCnt;q++) IJ.log((q+1)+":"+tPart[pIndex].sniffScore[q]+","+tPart[pIndex].sniffPos[q]);
							}*/
							int limit=1;
							boolean nxt=false;
							
								while (tPart[pIndex].sniffPos[limit]!=j) {
								//IJ.write(f+":"+j+"-"+tPart[pIndex].sniffPos[limit-1]+","+cPart.area+","+cPart.weight);
								
									limit++;
								}
							
							
							for (int l=0;l<limit;l++){
								if (tracks[tPart[pIndex].sniffPos[l]].sniffPos[0]==pIndex) {
									k++;
									nxt=true;
									break;
								}
							}
							if (!nxt){
								linked=true;
							}
						}
						else {linked=true;}
					}while((!linked)&&(k<tracks[j].sniffCnt));
					if (linked){
						if (cPart.area==-1) {
							tracks[j].element[tracks[j].boundD-1].centerX=(cPart.centerX+tPart[pIndex].element[0].centerX)/2;
							tracks[j].element[tracks[j].boundD-1].centerY=(cPart.centerY+tPart[pIndex].element[0].centerY)/2;
						}
						tracks[j].linkParticle(tPart[pIndex].element[0]);
						int cPos=tPart[pIndex].element[0].intcX+tPart[pIndex].element[0].intcY*W;
						pMap[f][cPos]=j;
						tMap[cPos]=0;
					}
					tracks[j].sniffCnt=0;
				}
				else if ((BLINKING>1)&&(tracks[j].sniffCnt==0)&&(cPart.area>0)){
					Particle tmP=new Particle(cPart.centerX,cPart.centerY,-1,cPart.intcX,cPart.intcY,0,0);
					tracks[j].linkParticle(tmP);
					int cPos=cPart.intcX+cPart.intcY*W;
					pMap[f][cPos]=j;
					//tMap[cOffset]=0;
				}
				else {tracks[j].sniffCnt=-1;}
			}

			if (cIndex+ppFrame[f].boundD>tracks.length) extendTracks(cIndex+2*ppFrame[f].boundD);
				for (j=0;j<max;j++) {
					if ((tMap[j]>0)&&(!ppFrame[f].element[tMap[j]-1].ghost)){
						tracks[cIndex]=new ParticleChain(ppFrame[f].element[tMap[j]-1],f+1);
						pMap[f][j]=cIndex;
						cIndex++;
					}
					tMap[j]=0;
			}
		}
		for (i=1;i<cIndex;i++) {
			if (tracks[i].last().area<0) tracks[i].removeLast();
			tracks[i].classified=true;
		}
		
	}
	void extendTracks(int n){
		ParticleChain[] tmPC=new ParticleChain[n];
		if (cIndex>1) System.arraycopy(tracks, 0, tmPC, 0, cIndex);
        this.tracks = tmPC;
	}
	
	
	void drawAllTracks(ParticleChain[] tracks,int cIndex, int W, int H){
		ImagePlus PathAll = NewImage.createRGBImage("AllPaths", W, H,1, NewImage.FILL_BLACK);
		ImageProcessor cp=PathAll.getProcessor();	
		int[] clor={0xffffff,0x8888ff,0xffff00,0x00ffff};
		for (int i=1;i<cIndex;i++){
			if (tracks[i].boundD>3){
				cp.setColor(Color.RED);
				cp.drawLine(tracks[i].element[1].intcX,tracks[i].element[1].intcY,tracks[i].element[2].intcX,tracks[i].element[2].intcY);
				int j=0;
				for (j=2;j<tracks[i].boundD-1;j++){
					if (tracks[i].element[j].area==-1) cp.setColor(Color.MAGENTA);
					else cp.setColor(clor[i%4]);
					cp.drawLine(tracks[i].element[j-1].intcX,tracks[i].element[j-1].intcY,tracks[i].element[j].intcX,tracks[i].element[j].intcY);
				}
				cp.setColor(Color.GREEN);
				cp.drawLine(tracks[i].element[j-1].intcX,tracks[i].element[j-1].intcY,tracks[i].element[j].intcX,tracks[i].element[j].intcY);
			}
		}
		PathAll.updateAndDraw();
		PathAll.show();
	}
	void drawPreview(Particle[] Particles){
		int clor=0;
		GeneralPath simpleParts=new GeneralPath();
		GeneralPath mergedParts=new GeneralPath();
		GeneralPath ghostParts=new GeneralPath();
		Color simpleColor=Color.GREEN;
		Color mergedColor=Color.YELLOW;
		Color ghostColor=Color.MAGENTA;
		BasicStroke stroke=new BasicStroke(0.5f);
		if (showPreview){
			for (int i=0;i<Particles.length;i++){
				if (Particles[i]!=null){
					if (Particles[i].bckg==0||(float)Particles[i].brightest/Particles[i].bckg>=SNR){
						if (Particles[i].merged) {clor=0xffff00; mergedParts.append(Particles[i].shpe, false); }
						else {clor=0x00ff00; simpleParts.append(Particles[i].shpe, false); }
					}
					else {clor=0xff0000; ghostParts.append(Particles[i].shpe, false); }
				}
			}
		}
		Overlay ol=new Overlay();
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
	

	void doStatistics(Particle[] P){
		ResultsTable res=new ResultsTable();
		res.setPrecision(0);
		res.setHeading(0,"Area");
		res.setHeading(1,"Weight");
		res.addColumns();	
		for (int i=1;i<P.length;i++){
			res.incrementCounter();
			res.addValue(0,P[i].area);
			res.addValue(1,P[i].weight);
		}
		
		res.show("Results");	
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
}