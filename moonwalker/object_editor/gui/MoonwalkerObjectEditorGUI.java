/*
    Copyright (C) 2020 Micha³ Kullass

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package moonwalker.object_editor.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.IntFunction;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;
import moonwalker.core.structures.MDirectObject;
import moonwalker.core.structures.MoonwalkerPalette;
import moonwalker.core.utils.MoonwalkerIO;
import moonwalker.core.utils.MoonwalkerMetadata;
import moonwalker.core.utils.REV00Metadata;

public class MoonwalkerObjectEditorGUI extends JFrame
{
	private File rom;
	
	private JTabbedPane tabPane;
	private JTabbedPane mainTabPane;
	private JTabbedPane caveTabPane;
	
	private ArrayList<MDirectObject>[] mainObjectArr;
//	private ArrayList<MDirectObject>[] caveObjectArr;
	
	private MoonwalkerMetadata meta;
	
	private StageDisplay[] stageDisplayArr;
	
	private JFileChooser openDialog, saveDialog;
	private FileFilter binFileFilter;
	
	private ScheduledThreadPoolExecutor zoomAnimationExecutor;
	
	private Preferences prefs;
	
	private final static String VERSION = "0.7.0";
	private AboutDialog aboutDialog;
	
	public MoonwalkerObjectEditorGUI(File romFile)
	{
		setLocationByPlatform(true);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLayout(new BorderLayout());
		setTitle("Moonwalker Object Editor v" + VERSION);
		setSize(900, 600);
		
		prefs = Preferences.userNodeForPackage(MoonwalkerObjectEditorGUI.class);
		
		binFileFilter = new FileFilter()
		{
			@Override
			public String getDescription()
			{
				return "Binary file (.bin)";
			}
			
			@Override
			public boolean accept(File f)
			{
				return f.isDirectory() || f.getName().toLowerCase().endsWith(".bin");
			}
		};
		
		SwingUtilities.invokeLater(() ->
		{
			if (openDialog == null)
			{
				try
				{
					openDialog = new JFileChooser(prefs.get("OpenDialogPath", ""));
				}
				catch (Exception e)
				{
					openDialog = new JFileChooser();
				}
				openDialog.setDialogTitle("Select a Rom file to open");
				openDialog.setFileFilter(binFileFilter);
				openDialog.setAcceptAllFileFilterUsed(false);
			}
			if (openDialog.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
			{
				try
				{
					//TODO implement other versions
					JPanel romVersionPanel = createRomVersionPanel();
					if (JOptionPane.showConfirmDialog(MoonwalkerObjectEditorGUI.this, romVersionPanel,
							"Select the ROM version",
							JOptionPane.OK_CANCEL_OPTION,
							JOptionPane.PLAIN_MESSAGE
							) == JOptionPane.OK_OPTION)
					{
						File f = openDialog.getSelectedFile();
						prefs.put("OpenDialogPath", f.getParent());
						
						byte[] romContent = Files.readAllBytes(f.toPath());
						meta = new REV00Metadata(romContent);
						MDirectObject[][] oArr = MoonwalkerIO.loadMainObjectArray(romContent, meta);
						
						mainObjectArr = new ArrayList[oArr.length];
						for (int i = 0; i < mainObjectArr.length; i++)
							mainObjectArr[i] = new ArrayList<MDirectObject>(Arrays.asList(oArr[i]));
						rom = f;
						
						initFrame(romContent);
					}
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
					CustomDialogs.showExceptionDialog(this, limitString("Unable to load ROM file. \nReason: "
								+ ex.getMessage() + " (" + ex.getClass().getTypeName() + ")"
								, 100),
							"Error", ex);
				}
			}
			
			if (mainObjectArr == null || rom == null)
				dispose();
		});
	}
	private void initFrame(byte[] romContent)
	{
		HashMap<Integer, Color> outlineColorMap = new HashMap<>();
		HashMap<Integer, Color> fillColorMap = new HashMap<>();
		fillMaps(outlineColorMap, fillColorMap);
		
		ArrayList<StageDisplay> stageDisplayList = new ArrayList<>();
		
		zoomAnimationExecutor = new ScheduledThreadPoolExecutor(1);
		
		tabPane = new JTabbedPane();
		
		mainTabPane = new JTabbedPane();
		int imgIndex = 0;
		for (; imgIndex < mainObjectArr.length; imgIndex++)
		{
			try
			{
				String stageName;
				if (imgIndex < (mainObjectArr.length - 1))
					stageName = ((imgIndex / 3) + 1) + "-" + ((imgIndex % 3) + 1);
				else
					stageName = ((imgIndex / 3) + 1) + "";
				
				MoonwalkerPalette pal = MoonwalkerIO.loadPalette(romContent, imgIndex, meta);
				BufferedImage img = optimizeImage(MoonwalkerIO.loadMainStageArea(romContent, imgIndex, meta)
						.createLayerA(pal, false));
				
				int ind = imgIndex;
				
				StageDisplay stageDisplay = new StageDisplay(mainObjectArr[ind], meta, img, outlineColorMap, fillColorMap,
						prefs, zoomAnimationExecutor);
				
				JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true,
						stageDisplay.getImagePanel(), stageDisplay.getDescriptionPanel());
				splitPane.setResizeWeight(1);
				
				stageDisplayList.add(stageDisplay);
				mainTabPane.addTab(stageName, splitPane);
				
				ChangeListener chL = new ChangeListener()
				{
					@Override
					public void stateChanged(ChangeEvent e)
					{
						if (mainTabPane.getSelectedIndex() == ind)
						{
							splitPane.setDividerLocation(splitPane.getSize().width - 200);
							mainTabPane.removeChangeListener(this);
						}
					}
				};
				mainTabPane.addChangeListener(chL);
				SwingUtilities.invokeLater(() ->
				{
					splitPane.setDividerLocation(splitPane.getSize().width - 200);
				});
			}
			catch (Exception e)
			{}
		}
		if (mainTabPane.getTabCount() <= 0)
		{
			JPanel panel = new JPanel();
			JLabel label = new JLabel("[No map image found.]");
			label.setForeground(Color.RED);
			panel.add(label);
			tabPane.addTab("Main Stages", panel);
		}
		else
			tabPane.addTab("Main Stages", mainTabPane);
		
		caveTabPane = new JTabbedPane();
		
		//TODO implement caves
		
		if (caveTabPane.getTabCount() <= 0)
		{
			JPanel panel = new JPanel();
			JLabel label = new JLabel("[No map image found.]");
			label.setForeground(Color.RED);
			panel.add(label);
			tabPane.addTab("Caves", panel);
		}
		else
			tabPane.addTab("Caves", caveTabPane);
		
		//TODO remove once caves are implemented
		tabPane.setEnabledAt(1, false);
		
		add(tabPane, BorderLayout.CENTER);
		
		stageDisplayArr = stageDisplayList.toArray(l -> new StageDisplay[l]);
		
		zoomAnimationExecutor = new ScheduledThreadPoolExecutor(1);
		
		JPanel southPanel = new JPanel();
		
		JButton bAdd = new JButton("Add");
		
		Runnable addAction = () -> stageDisplayArr[mainTabPane.getSelectedIndex()].showAddDialog(this);
		Runnable editAction = () ->
		{
			int stageIndex = mainTabPane.getSelectedIndex();
			if (stageIndex < 0)
			{
				JOptionPane.showMessageDialog(this, "No stage selected.", "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}
			StageDisplay stageDisp = stageDisplayArr[stageIndex];
			MDirectObject object = stageDisp.getSelectedObject();
			if (object == null)
			{
				JOptionPane.showMessageDialog(this, "No object selected.", "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}
			
			StageDisplay sd = stageDisplayArr[stageIndex];
			sd.showEditDialog(this, object);
		};
		Callable<Boolean> silentRemoveAction = () ->
		{
			int stageIndex = mainTabPane.getSelectedIndex();
			if (stageIndex < 0)
				return Boolean.FALSE;
			StageDisplay stageDisp = stageDisplayArr[stageIndex];
			MDirectObject object = stageDisp.getSelectedObject();
			if (object == null)
				return Boolean.FALSE;
			
			stageDisp.setSelectedObject(null);
			mainObjectArr[stageIndex].remove(object);
			
			mainTabPane.repaint();
			
			return Boolean.TRUE;
		};
		
		bAdd.addActionListener(e -> addAction.run());
		
		southPanel.add(bAdd);
		southPanel.add(createSeparator(2, 20, JSeparator.VERTICAL));
		
		JButton bSave = new JButton("Save");
		JButton bLoad = new JButton("Load");
		
		Runnable saveAction = () ->
		{
			if (saveDialog == null)
			{
				try
				{
					saveDialog = new JFileChooser(prefs.get("SaveDialogPath", rom.getAbsoluteFile().getParent()));
				}
				catch (Exception e)
				{
					saveDialog = new JFileChooser();
				}
				saveDialog.setDialogTitle("Select a path for the Rom file");
				saveDialog.setFileFilter(binFileFilter);
				saveDialog.setAcceptAllFileFilterUsed(false);
			}
			if (saveDialog.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
			{
				File f = saveDialog.getSelectedFile();
				prefs.put("SaveDialogPath", f.getParent());
				if (f.equals(rom) && (JOptionPane.showConfirmDialog(this,
							"The selected file is the same as the source Rom. Are you sure you want to continue?",
							"Moonwalker Object Editor",
							JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION))
						return;
				
				try
				{
					byte[] modifiedRom = Arrays.copyOf(romContent, romContent.length);
					MoonwalkerIO.saveMainObjectArray(modifiedRom, deepToArray(mainObjectArr), meta);
					MoonwalkerIO.fixChecksum(modifiedRom);
					try (FileOutputStream fos = new FileOutputStream(f))
					{
						fos.write(modifiedRom);
					}
					JOptionPane.showMessageDialog(this, "Rom saved successfully.", "Moonwalker Object Editor", JOptionPane.INFORMATION_MESSAGE);
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
					CustomDialogs.showExceptionDialog(this, limitString("Unable to create ROM file. \nReason: "
								+ ex.getMessage() + " (" + ex.getClass().getTypeName() + ")"
								, 100),
							"Error", ex);
				}
			}
		};
		Runnable loadAction = () ->
		{
			if (openDialog == null)
			{
				try
				{
					openDialog = new JFileChooser(prefs.get("OpenDialogPath", ""));
				}
				catch (Exception e)
				{
					openDialog = new JFileChooser();
				}
				openDialog.setDialogTitle("Select a Rom file to open");
				openDialog.setFileFilter(binFileFilter);
				openDialog.setAcceptAllFileFilterUsed(false);
			}
			if (openDialog.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
			{
				try
				{
					File f = openDialog.getSelectedFile();
					prefs.put("OpenDialogPath", f.getParent());
					
					byte[] romCont = Files.readAllBytes(f.toPath());
					meta = new REV00Metadata(romCont);
					MDirectObject[][] oArr = MoonwalkerIO.loadMainObjectArray(romCont, meta);
					if (mainTabPane.getTabCount() > oArr.length)
						throw new IllegalStateException("Number of maps exceeds number of stages: " + mainTabPane.getTabCount() + ", " + oArr.length);
					
					mainObjectArr = new ArrayList[oArr.length];
					for (int i = 0; i < mainObjectArr.length; i++)
						mainObjectArr[i] = new ArrayList<MDirectObject>(Arrays.asList(oArr[i]));
					rom = f;
					
					for (int i = 0; i < mainObjectArr.length; i++)
					{
						StageDisplay sd = stageDisplayArr[i];
						sd.setObjects(mainObjectArr[i]);
						
						MoonwalkerPalette pal = MoonwalkerIO.loadPalette(romContent, i, meta);
						BufferedImage img = optimizeImage(MoonwalkerIO.loadMainStageArea(romCont, i, meta)
								.createLayerA(pal, false));
						sd.setImage(img);
					}
					
					mainTabPane.repaint();
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
					CustomDialogs.showExceptionDialog(this, limitString("Unable to load ROM file. \nReason: "
								+ ex.getMessage() + " (" + ex.getClass().getTypeName() + ")"
								, 100),
							"Error", ex);
				}
			}
		};
		bLoad.addActionListener(e -> loadAction.run());
		bSave.addActionListener(e -> saveAction.run());
		
		if (mainTabPane.getTabCount() < 1)
		{
			bSave.setEnabled(false);
			bLoad.setEnabled(false);
		}
		
		southPanel.add(bSave);
		southPanel.add(bLoad);
		
		add(southPanel, BorderLayout.SOUTH);
		
		JMenuBar menuBar = new JMenuBar();
		
		JMenu mFile = new JMenu("File");
		JMenuItem mOpen = new JMenuItem("Open...");
		JMenuItem mSave = new JMenuItem("Save...");
		JMenuItem mScale = new JMenuItem("Set scale...");
		JCheckBoxMenuItem mSmoothZoom = new JCheckBoxMenuItem("Enable smooth zoom");
		JMenuItem mExit = new JMenuItem("Exit");
		
		mOpen.addActionListener(e -> loadAction.run());
		mSave.addActionListener(e -> saveAction.run());
		mScale.addActionListener(e ->
		{
			StageDisplay sd = stageDisplayArr[mainTabPane.getSelectedIndex()];
			
			double scale = sd.getScale();
			double minScale = sd.getMinScale();
			double maxScale = sd.getMaxScale();
			
			String s = JOptionPane.showInputDialog("Enter the new scale factor:", scale);
			if (s == null)
				return;
			
			try
			{
				double newScale = Double.parseDouble(s);
				if (newScale > maxScale)
					newScale = maxScale;
				else if (newScale < minScale)
					newScale = minScale;
				sd.setScale(newScale);
				mainTabPane.repaint();
			}
			catch (Exception ex)
			{
				JOptionPane.showMessageDialog(MoonwalkerObjectEditorGUI.this, "Invalid value: \"" + limitString(s, 50) + "\"", "Scale", JOptionPane.ERROR_MESSAGE);
			}
		});
		mSmoothZoom.addChangeListener(e ->
		{
			prefs.putBoolean("EnableSmoothZoom", mSmoothZoom.isSelected());
		});
		mSmoothZoom.setSelected(prefs.getBoolean("EnableSmoothZoom", true));
		mExit.addActionListener(e ->
		{
			dispose();
		});
		
		mFile.add(mOpen);
		mFile.add(mSave);
		mFile.addSeparator();
		mFile.add(mScale);
		mFile.addSeparator();
		mFile.add(mSmoothZoom);
		mFile.addSeparator();
		mFile.add(mExit);
		
		JMenu mEdit = new JMenu("Edit");
		
		JMenuItem mAdd = new JMenuItem("Add...");
		JMenuItem mEditItem = new JMenuItem("Edit...");
		JMenuItem mRemove = new JMenuItem("Remove");
		
		mAdd.addActionListener(e -> addAction.run());
		mEditItem.addActionListener(e -> editAction.run());
		mRemove.addActionListener(e ->
		{
			boolean failure = true;
			try
			{
				if (silentRemoveAction.call())
					failure = false;
			}
			catch (Exception ex)
			{}
			
			if (failure)
				JOptionPane.showMessageDialog(this, "No object selected.", "Error", JOptionPane.ERROR_MESSAGE);
		});
		
		mEdit.add(mAdd);
		mEdit.add(mEditItem);
		mEdit.add(mRemove);
		
		JMenu mHelp = new JMenu("Help");
		
		JMenuItem miHelp = new JMenuItem("Help");
		//TODO implement
		miHelp.setEnabled(false);
		JMenuItem mAbout = new JMenuItem("About MWOE");
		mAbout.addActionListener(e -> 
		{
			if (aboutDialog == null)
				aboutDialog = new AboutDialog(VERSION, this);
			aboutDialog.show();
		});
		
		mHelp.add(miHelp);
		mHelp.add(mAbout);
		
		menuBar.add(mFile);
		menuBar.add(mEdit);
		menuBar.add(mHelp);
		
		setJMenuBar(menuBar);
		
		InputMap tabPaneInputMap = mainTabPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		ActionMap tabPaneActionMap = mainTabPane.getActionMap();
		
		tabPaneInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeMoonwalkerObjectAction");
		tabPaneInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "removeMoonwalkerObjectAction");
		tabPaneActionMap.put("removeMoonwalkerObjectAction", lambdaToAction(e ->
		{
			try
			{
				silentRemoveAction.call();
			}
			catch (Exception ex)
			{}
		}));
		
		tabPaneInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK), "editMoonwalkerObjectAction");
		tabPaneActionMap.put("editMoonwalkerObjectAction", lambdaToAction(e -> editAction.run()));
		
		//TODO implement undo/redo functionality
//		tabPaneInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undoAction");
//		tabPaneActionMap.put("editMoonwalkerObjectAction", lambdaToAction(e -> 
//		{
//			int ind = tabPane.getSelectedIndex();
//			if (ind < 0)
//				return;
//			
//			UndoManager undoMan = undoManArr[ind];
//			if (undoMan.canUndo())
//				undoMan.undo();
//		}));
//		
//		tabPaneInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redoAction");
//		tabPaneActionMap.put("editMoonwalkerObjectAction", lambdaToAction(e -> 
//		{
//			int ind = tabPane.getSelectedIndex();
//			if (ind < 0)
//				return;
//			
//			UndoManager undoMan = undoManArr[ind];
//			if (undoMan.canRedo())
//				undoMan.redo();
//		}));
		
		revalidate();
		repaint();
	}
	
	private BufferedImage optimizeImage(BufferedImage src)
	{
		try
		{
			int width = src.getWidth();
			int height = src.getHeight();
			BufferedImage ret = GraphicsEnvironment
					.getLocalGraphicsEnvironment()
					.getDefaultScreenDevice()
					.getDefaultConfiguration()
					.createCompatibleImage(width, height, src.getTransparency());
			
			Graphics2D g2d = ret.createGraphics();
			g2d.drawImage(src, 0, 0, width, height, null);
			g2d.dispose();
			ret.setAccelerationPriority(1);
			
			return ret;
		}
		catch (Exception e)
		{
			return src;
		}
	}
	private static JSeparator createSeparator(int width, int height, int orientation)
	{
		JSeparator sep = new JSeparator(orientation);
		sep.setPreferredSize(new Dimension(width, height));
		sep.setOpaque(false);
		return sep;
	}
	private static AbstractAction lambdaToAction(ActionLambda al)
	{
		return new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				al.actionPerformed(e);
			}
		};
	}
	private static MDirectObject[][] deepToArray(ArrayList<MDirectObject>[] listArr)
	{
		MDirectObject[][] ret = new MDirectObject[listArr.length][];
		for (int i = 0; i < ret.length; i++)
			ret[i] = listArr[i].toArray(l -> new MDirectObject[l]);
		return ret;
	}
	private JPanel createRomVersionPanel()
	{
		//TODO add parameter of type List<..., Supplier<Boolean>> for button state access
		JPanel dialogPanel = new JPanel();
		dialogPanel.setLayout(new BoxLayout(dialogPanel, BoxLayout.Y_AXIS));
		
		JRadioButton bREV00 = new JRadioButton("REV00");
		JRadioButton bREV01 = new JRadioButton("REV01");
		JRadioButton bPrototype = new JRadioButton("Prototype");
		JRadioButton bDetect = new JRadioButton("Detect automatically");
		JRadioButton bCustom = new JRadioButton("Custom...");
		
		bREV01.setEnabled(false);
		bPrototype.setEnabled(false);
		bDetect.setEnabled(false);
		bCustom.setEnabled(false);
		
		ButtonGroup buGroup = new ButtonGroup();
		buGroup.add(bREV00);
		buGroup.add(bREV01);
		buGroup.add(bPrototype);
		buGroup.add(bDetect);
		buGroup.add(bCustom);
		
		dialogPanel.add(bREV00);
		dialogPanel.add(bREV01);
		dialogPanel.add(bPrototype);
		dialogPanel.add(bDetect);
		dialogPanel.add(bCustom);
		
		bREV00.setSelected(true);
		
		return dialogPanel;
	}
	
	private void fillMaps(HashMap<Integer, Color> outlineColorMap, HashMap<Integer, Color> fillColorMap)
	{
		//TODO find a better way to init hashmaps, then refactor
		
		outlineColorMap.put(0x0001, new Color(255, 255, 255));
		outlineColorMap.put(0x0002, new Color(128, 128, 128));
		outlineColorMap.put(0x0004, new Color(255, 255, 255));
		outlineColorMap.put(0x0005, new Color(160, 0, 0));
		outlineColorMap.put(0x000C, new Color(255, 0, 0));
		outlineColorMap.put(0x000D, new Color(150, 0, 200));
		outlineColorMap.put(0x000E, new Color(255, 128, 0));
		
		outlineColorMap.put(0x0014, new Color(0, 255, 0));
		outlineColorMap.put(0x0015, new Color(255, 0, 255));
		
		outlineColorMap.put(0x0016, new Color(64, 64, 222));
		outlineColorMap.put(0x0017, new Color(128, 128, 222));
		outlineColorMap.put(0x0018, new Color(224, 224, 0));				
		outlineColorMap.put(0x0019, new Color(255, 0, 0));
		outlineColorMap.put(0x001B, new Color(64, 64, 64));
		outlineColorMap.put(0x001C, new Color(255, 0, 255));
		outlineColorMap.put(0x001E, new Color(200, 64, 16));
		outlineColorMap.put(0x001F, new Color(255, 255, 255));
		outlineColorMap.put(0x0020, new Color(158, 255, 250));
		outlineColorMap.put(0x0021, new Color(20, 224, 163));
		outlineColorMap.put(0x0023, new Color(47, 163, 51));
		outlineColorMap.put(0x0024, new Color(72, 72, 72));
		outlineColorMap.put(0x0025, new Color(170, 255, 0));
		outlineColorMap.put(0x0026, new Color(207, 187, 104));
		outlineColorMap.put(0x0029, new Color(162, 207, 14));
		outlineColorMap.put(0x002A, new Color(140, 220, 255));
		outlineColorMap.put(0x002D, new Color(16, 224, 16));
		outlineColorMap.put(0x002E, new Color(224, 186, 164));
		outlineColorMap.put(0x002F, new Color(255, 0, 200));
		outlineColorMap.put(0x0030, new Color(210, 194, 24));
		outlineColorMap.put(0x0034, new Color(255, 175, 10));
		outlineColorMap.put(0x0035, new Color(160, 32, 160));
		outlineColorMap.put(0x0036, new Color(150, 190, 210));
		outlineColorMap.put(0x0037, new Color(250, 106, 40));
		outlineColorMap.put(0x0049, new Color(194, 0, 0));
		outlineColorMap.put(0x004C, new Color(234, 212, 0));
		outlineColorMap.put(0x0050, new Color(0, 0, 255));
		outlineColorMap.put(0x0055, new Color(0, 255, 0));
		outlineColorMap.put(0x0057, new Color(64, 224, 255));
		outlineColorMap.put(0x0059, new Color(36, 72, 255));
		outlineColorMap.put(0x005A, new Color(128, 128, 128));
		outlineColorMap.put(0x005B, new Color(58, 96, 255));
		outlineColorMap.put(0x005D, new Color(160, 80, 48));
		outlineColorMap.put(0x005F, new Color(0, 128, 64));
		outlineColorMap.put(0x0060, new Color(16, 164, 96));
		outlineColorMap.put(0x0061, new Color(128, 0, 64));
		outlineColorMap.put(0x0062, new Color(255, 255, 0));
		outlineColorMap.put(0x0065, new Color(112, 0, 0));
		outlineColorMap.put(0x007D, new Color(255, 0, 0));
		
		//----------------
		
		fillColorMap.put(0x0001, new Color(255, 0, 0, 128));
		fillColorMap.put(0x0002, new Color(128, 128, 128, 128));
		fillColorMap.put(0x0004, new Color(128, 128, 128, 128));
		fillColorMap.put(0x0005, new Color(192, 192, 0, 152));
		fillColorMap.put(0x000C, new Color(255, 255, 0, 128));
		fillColorMap.put(0x000D, new Color(150, 0, 200, 128));
		fillColorMap.put(0x000E, new Color(255, 128, 0, 128));
		
		fillColorMap.put(0x0014, new Color(255, 255, 255, 128));
		fillColorMap.put(0x0015, new Color(255, 255, 255, 128));
		
		fillColorMap.put(0x0016, new Color(64, 64, 222, 128));
		fillColorMap.put(0x0017, new Color(80, 255, 110, 128));
		fillColorMap.put(0x0018, new Color(200, 64, 16, 128));				
		fillColorMap.put(0x0019, new Color(255, 0, 0, 128));
		fillColorMap.put(0x001B, new Color(121, 121, 148, 128));
		fillColorMap.put(0x001C, new Color(0, 0, 255, 128));
		fillColorMap.put(0x001E, new Color(255, 255, 0, 196));
		fillColorMap.put(0x001F, new Color(255, 255, 128, 128));
		fillColorMap.put(0x0020, new Color(133, 222, 242, 128));
		fillColorMap.put(0x0021, new Color(136, 20, 224, 128));
		fillColorMap.put(0x0023, new Color(180, 20, 60, 160));
		fillColorMap.put(0x0024, new Color(150, 190, 210, 196));
		fillColorMap.put(0x0025, new Color(128, 255, 128, 128));
		fillColorMap.put(0x0026, new Color(33, 0, 107, 128));
		fillColorMap.put(0x0029, new Color(255, 95, 10, 160));
		fillColorMap.put(0x002A, new Color(64, 64, 164, 128));
		fillColorMap.put(0x002D, new Color(32, 196, 96, 128));
		fillColorMap.put(0x002E, new Color(200, 64, 16, 128));
		fillColorMap.put(0x002F, new Color(209, 202, 59, 128));
		fillColorMap.put(0x0030, new Color(240, 224, 16, 128));
		fillColorMap.put(0x0034, new Color(162, 207, 14, 160));
		fillColorMap.put(0x0035, new Color(128, 128, 128, 128));
		fillColorMap.put(0x0036, new Color(160, 32, 160, 128));
		fillColorMap.put(0x0037, new Color(250, 106, 40, 128));
		fillColorMap.put(0x0049, new Color(255, 255, 128, 128));
		fillColorMap.put(0x004C, new Color(237, 7, 255, 192));
		fillColorMap.put(0x0050, new Color(0, 0, 255, 128));
		fillColorMap.put(0x0055, new Color(0, 255, 0, 128));
		fillColorMap.put(0x0057, new Color(64, 224, 255, 128));
		fillColorMap.put(0x0059, new Color(64, 96, 224, 128));
		fillColorMap.put(0x005A, new Color(0, 0, 255, 128));
		fillColorMap.put(0x005B, new Color(49, 102, 236, 128));
		fillColorMap.put(0x005D, new Color(0, 0, 0, 128));
		fillColorMap.put(0x005F, new Color(232, 210, 128, 128));
		fillColorMap.put(0x0060, new Color(200, 196, 64, 128));
		fillColorMap.put(0x0061, new Color(128, 0, 64, 128));
		fillColorMap.put(0x0062, new Color(255, 255, 0, 128));
		fillColorMap.put(0x0065, new Color(72, 64, 64, 144));
		fillColorMap.put(0x007D, new Color(0, 255, 255, 128));
	}
	private static String limitString(String s, int limit)
	{
		if (s.length() > limit)
		{
			return s.substring(0, limit) + " ...";
		}
		return s;
	}
	
	public static void main(String[] args)
	{
		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			UIManager.getDefaults().entrySet().forEach((t) ->
			{
				if (t.getKey() instanceof String)
				{
					if (((String) t.getKey()).endsWith(".focus"))
					{
						t.setValue(new Color(0, 0, 0, 0));
					}
				}
			});
			JFrame.setDefaultLookAndFeelDecorated(false);
		}
		catch (Exception e)
		{}
		
		try
		{
			Class.forName("java.util.function.IntFunction");
			ArrayList.class.getMethod("toArray", IntFunction.class);
		}
		catch (Exception e)
		{
			if (!(e instanceof SecurityException))
			{
				int res = JOptionPane.showOptionDialog(null,
						"This program requires Java version 11 or higher. "
						+ "Please download the appropriate version before proceeding.\n"
						+ "If you have already installed Java 11 and are still "
						+ "getting this error, make sure your operating system is using "
						+ "the right Java version to open this program.\n\n"
						+ "Current Java version: " + System.getProperty("java.version"),
						"Error", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null,
						new String[] {"Proceed anyway", "Exit"}, "Exit");
				
				if (res != JOptionPane.OK_OPTION)
					System.exit(0);
			}
		}
		
		SwingUtilities.invokeLater(() ->
		{
			try
			{
				MoonwalkerObjectEditorGUI app = new MoonwalkerObjectEditorGUI(new File("MW.bin"));
				app.setVisible(true);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				CustomDialogs.showExceptionDialog(null,
						limitString("Cannot launch application. Reason: "
								+ e.getMessage() + " (" + e.getClass().getTypeName() + ")"
								, 75),
						"Error", e);
			}
		});
	}
	
	private static interface ActionLambda
	{
		public abstract void actionPerformed(ActionEvent e);
	}
	
	private static class CustomDialogs
	{
		public static void showExceptionDialog(Component parent, String message, String title, Exception exc)
		{
			JPanel content = new JPanel(new BorderLayout());
			JLabel msgLabel = new JLabel((message == null)?null:(message.startsWith("<html>")?message:("<html>" + message.replace("\n", "<br>") + "</html>")));
			content.add(msgLabel, BorderLayout.NORTH);
			
			JPanel centerPanel = new JPanel();
			centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
			
			JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			JButton button = new JButton("\u25BC");
			button.setMargin(new Insets(0, 4, 0, 4));
			buttonPanel.add(button);
			JLabel buttonLabel = new JLabel("Show details");
			buttonPanel.add(buttonLabel);
			buttonPanel.setMaximumSize(new Dimension(buttonPanel.getMaximumSize().width, buttonPanel.getPreferredSize().height));
			
			centerPanel.add(buttonPanel);
			JTextArea ta = new JTextArea();
			ta.setEditable(false);
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			if (exc != null)
				exc.printStackTrace(pw);
			else
				pw.print((String) null);
			ta.setText(sw.toString());
			pw.close();
			JScrollPane sTa = new JScrollPane(ta);
			sTa.setPreferredSize(new Dimension(sTa.getPreferredSize().width, (int) Math.min(sTa.getPreferredSize().height * 1.5, 115)));
			sTa.setMaximumSize(new Dimension(sTa.getMaximumSize().width, (int) Math.min(sTa.getMaximumSize().height * 1.5, 115)));
			sTa.setVisible(false);
			button.addActionListener(e ->
			{
				if (sTa.isVisible())
				{
					sTa.setVisible(false);
					button.setText("\u25BC");
					buttonLabel.setText("Show details");
				}
				else
				{
					sTa.setVisible(true);
					button.setText("\u25B2");
					buttonLabel.setText("Hide details");
				}
				SwingUtilities.getWindowAncestor(content).pack();
			});
			centerPanel.add(sTa);
			
			content.add(centerPanel, BorderLayout.CENTER);
			JOptionPane.showMessageDialog(parent, content, title, JOptionPane.ERROR_MESSAGE);
		}
	}
}
