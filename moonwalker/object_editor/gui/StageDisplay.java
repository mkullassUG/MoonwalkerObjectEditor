package moonwalker.object_editor.gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import moonwalker.core.structures.MDirectObject;
import moonwalker.core.utils.MoonwalkerMetadata;

class StageDisplay
{
	private ArrayList<MDirectObject> objectArr;
	private BufferedImage img;
	private Preferences prefs;
	private MoonwalkerMetadata meta;
	
	private double minScale, maxScale;
	private double xPos, yPos, scale;
	
	private JPanel imgPanel;
	private JPanel descriptionPanel;
	
	private MDirectObject selectedObj;
	private MDirectObject.Container showFilter;
	
	private int selectionThreshold;
	
	private HashMap<Integer, Color> outlineColMap, fillColMap;
	
	private final Color stackStringColor = Color.RED;
	
	private Runnable updateDescriptionPanel;
	
	private ScheduledThreadPoolExecutor zoomAnimExecutor;
	private int zoomAnimationFrame;
	private double[] scaleChangeArr;
	private double[] xChangeArr;
	private double[] yChangeArr;
	private Future<?> zoomAnimationFuture;
	private final static int zoomAnimationFrameCount = 20;
	private final static int zoomAnimationFramePause = 6;
	
	private JDialog editDialog;
	private JLabel lAddressEdit;
	private JTextField tfTypeEdit;
	private JTextField tfDataEdit;
	private JComboBox<MDirectObject.Container> cbContainerEdit;
	private MDirectObject editedObject;
	private boolean editAddressChanged;
	
	private JDialog addrEditDialog;
	private MDirectObject editedAddrObject;
	private Runnable editAddrUpdateListener;
	private int addrEditSelectedAddress;
	
	private JDialog addDialog;
	private MDirectObject addObject;
	private JLabel lAddressAdd;
	private JTextField tfTypeAdd;
	private JTextField tfDataAdd;
	private JComboBox<MDirectObject.Container> cbContainerAdd;
	private boolean addAddressSelected;
	
	public StageDisplay(ArrayList<MDirectObject> objArr, MoonwalkerMetadata metadata, BufferedImage image,
			HashMap<Integer, Color> outlineColorMap, HashMap<Integer, Color> fillColorMap,
			Preferences preferences, ScheduledThreadPoolExecutor zoomAnimationExecutor)
	{
		objectArr = objArr;
		meta = metadata;
		img = image;
		prefs = preferences;
		zoomAnimExecutor = zoomAnimationExecutor;
		
		minScale = 0.2;
		maxScale = 1000;
		selectionThreshold = 5;
		
		int pointMult = 5;
		
		xPos = img.getWidth() / 2;
		yPos = img.getHeight() / 2;
		scale = 1;
		
		outlineColMap = outlineColorMap;
		fillColMap = fillColorMap;
		
		showFilter = MDirectObject.Container.ALL_TABLES;
		
		scaleChangeArr = new double[zoomAnimationFrameCount];
		xChangeArr = new double[zoomAnimationFrameCount];
		yChangeArr = new double[zoomAnimationFrameCount];
		
		imgPanel = new JPanel()
		{
			@Override
			public void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				
				if (img != null)
				{
					Graphics2D g2d = (Graphics2D) g.create();
					
					AffineTransform tra = createTransform(getWidth(), getHeight());
					
					g2d.drawImage(img, new AffineTransformOp(tra, AffineTransformOp.TYPE_NEAREST_NEIGHBOR), 0, 0);
					
					double size = pointMult * Math.sqrt(scale * scale + 1.5);

					g2d.setStroke(new BasicStroke((float) (size / pointMult * 0.5 + 1)));
					g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					
					final double scaleThreshold = 0.25;
					
					if (objectArr != null)
					{
						double sca = scale;
						
						HashMap<Point, Integer> intersectionMap = null;
						if (sca > scaleThreshold)
							intersectionMap = new HashMap<>();
						
						for (MDirectObject obj: objectArr)
						{
							if (!isExclusive(obj.getContainer(), showFilter))
								continue;
							
							int aX = obj.getAbsoluteX();
							int aY = obj.getAbsoluteY();
							
							Point2D p = new Point2D.Double(aX, aY);
							tra.transform(p, p);
							
							Ellipse2D ellip = new Ellipse2D.Double(p.getX() - (size / 2), p.getY() - (size / 2), size, size);
		    				
							g2d.setColor(fillColMap.getOrDefault(0xFFFF & obj.getType(), Color.BLACK));
		    				g2d.fill(ellip);
		    				g2d.setColor(outlineColMap.getOrDefault(0xFFFF & obj.getType(), Color.RED));
		    				g2d.draw(ellip);
		    				
		    				if (sca > scaleThreshold)
		    				{
			    				Point point = new Point(aX, aY);
			    				int n = intersectionMap.getOrDefault(point, 0);
			    				intersectionMap.put(point, n + 1);
		    				}
						}
						
						if (sca > scaleThreshold)
	    				{
							if (sca > 1.5)
								g2d.setColor(stackStringColor);
							else
							{
								int red = stackStringColor.getRed();
								int green = stackStringColor.getGreen();
								int blue = stackStringColor.getBlue();
								g2d.setColor(new Color(red, green, blue, (int) ((sca - scaleThreshold) * 204)));
							}
								
							for (Map.Entry<Point, Integer> entry: intersectionMap.entrySet())
							{
								int n = entry.getValue();
								if (n > 1)
								{
									Point point = entry.getKey();
									Point2D p = new Point2D.Double(point.x, point.y);
									tra.transform(p, p);
									
									g2d.drawString("" + n, (float) (p.getX() + (size / 2)), (float) (p.getY() - (size / 2)));
								}
							}
	    				}
						
						if (selectedObj != null)
						{
							int aX = selectedObj.getAbsoluteX();
							int aY = selectedObj.getAbsoluteY();
							
							Point2D p = new Point2D.Double(aX, aY);
							tra.transform(p, p);
							
							size *= 1.5;
							
							Rectangle2D rect = new Rectangle2D.Double(p.getX() - (size / 2), p.getY() - (size / 2), size, size);
							g2d.setColor(Color.RED);
							g2d.setStroke(new BasicStroke(2f,
									BasicStroke.CAP_ROUND,
									BasicStroke.JOIN_BEVEL,
									1,
									new float[]{7, 5},
									3.5f));
							g2d.draw(rect);
						}
					}
				}
			}
		};
		imgPanel.setFont(imgPanel.getFont().deriveFont(imgPanel.getFont().getSize2D() * 1.5f).deriveFont(Font.BOLD));
		
		imgPanel.addMouseWheelListener(e ->
		{
			try
			{
				final double tickSpeed = 7.5;
				
				double prevScale = scale;
				
				double tick = e.getPreciseWheelRotation();
				double s = tick / tickSpeed;
				
				double newScale = prevScale - (s * prevScale);
				double newX = xPos;
				double newY = yPos;
				
				boolean smoothZoom = prefs.getBoolean("EnableSmoothZoom", true);
				
				if (!smoothZoom)
				{
					newScale = defractionize(newScale);
					newScale = limit(newScale, minScale, maxScale);
				}
				
				if (((scale != minScale) && (scale != maxScale))
						|| ((newScale != minScale) && (newScale != maxScale)))
				{
					Point2D p = point2D(e.getPoint());
					
					AffineTransform tra = createTransform(imgPanel.getWidth(), imgPanel.getHeight(), newScale);
					
					tra.inverseTransform(p, p);
					
					newX += (p.getX() - xPos) * -s;
					newY += (p.getY() - yPos) * -s;
				}
				
				if (!smoothZoom)
				{
					scale = newScale;
					xPos = newX;
					yPos = newY;
					limitCoords(img.getWidth(), img.getHeight());
					imgPanel.repaint();
					return;
				}
				
				double destScale = newScale;
				double destX = newX;
				double destY = newY;
				
				synchronized (scaleChangeArr)
				{
					if (zoomAnimationFuture != null)
					{
						zoomAnimationFuture.cancel(true);
						zoomAnimationFuture = null;
						zoomAnimationFrame = 0;
					}
					zoomAnimationFuture = zoomAnimExecutor.scheduleAtFixedRate(() -> 
					{
						synchronized (scaleChangeArr)
						{
							if (zoomAnimationFrame == 0)
							{
								double scaleDiff = destScale - scale;
								double xDiff = destX - xPos;
								double yDiff = destY - yPos;
								
								double tickTotal = 0;
								
								for (int i = 0; i < zoomAnimationFrameCount; i++)
								{
									tickTotal += 1 / (i * i / 4.0 + 1);
								}
								for (int i = 0; i < zoomAnimationFrameCount; i++)
								{
									double diffTick = (1 / (i * i / 4.0 + 1)) / tickTotal;
									scaleChangeArr[i] = scaleDiff * diffTick;
									xChangeArr[i] = xDiff * diffTick;
									yChangeArr[i] = yDiff * diffTick;
								}
							}
							if (zoomAnimationFrame < zoomAnimationFrameCount)
							{
								double currScale = scale + scaleChangeArr[zoomAnimationFrame];
								currScale = limit(currScale, minScale, maxScale);
								currScale = defractionize(currScale);
								if (currScale != scale)
								{
									scale = currScale;
									xPos += xChangeArr[zoomAnimationFrame];
									yPos += yChangeArr[zoomAnimationFrame];
									limitCoords(img.getWidth(), img.getHeight());
									SwingUtilities.invokeLater(() -> imgPanel.repaint());
								}
								zoomAnimationFrame++;
							}
							else
							{
								if (zoomAnimationFuture != null)
								{
									Future<?> f = zoomAnimationFuture;
									zoomAnimationFuture = null;
									zoomAnimationFrame = 0;
									f.cancel(false);
								}
							}
						}
					}, 0, zoomAnimationFramePause, TimeUnit.MILLISECONDS);
				}
			}
			catch (Exception ex)
			{
				ex.printStackTrace();
			}
		});
		
		MouseAdapter dragAction = new MouseAdapter()
		{
			private boolean isDraggable;
			private Point prev;
			
			@Override
			public void mousePressed(MouseEvent e)
			{
				int button = e.getButton();
				if ((button == MouseEvent.BUTTON2) || (button == MouseEvent.BUTTON3))
				{
					isDraggable = true;
					prev = e.getPoint();
				}
				else
					isDraggable = false;
			}
			@Override
			public void mouseReleased(MouseEvent e)
			{
				isDraggable = false;
			}
			@Override
			public void mouseDragged(MouseEvent e)
			{
				if (isDraggable)
				{
					Point p = e.getPoint();
					xPos += (prev.x - p.x) / scale;
					yPos += (prev.y - p.y) / scale;
					
					limitCoords(img.getWidth(), img.getHeight());
						
					prev = p;
					imgPanel.paintImmediately(0, 0, imgPanel.getWidth(), imgPanel.getHeight());
				}
			}
		};
		
		imgPanel.addMouseListener(dragAction);
		imgPanel.addMouseMotionListener(dragAction);
		
		descriptionPanel = new JPanel();
		descriptionPanel.setLayout(new BoxLayout(descriptionPanel, BoxLayout.Y_AXIS));
		
		JLabel lType = new JLabel("[No object selected]");
		JLabel lDescr = new JLabel("");
		JLabel lRegion = new JLabel("");
		JLabel lRelative = new JLabel("");
		JLabel lAbsolute = new JLabel("");
		JLabel lAddress = new JLabel("");
		JLabel lData = new JLabel("");
		JLabel lContainer = new JLabel("");
		JButton bEdit = new JButton("Edit");
		JSeparator descrPSeparator = createBoxCompatibleSeparator(-1, 2, JSeparator.HORIZONTAL);
		JButton bRemove = new JButton("Remove");
		bEdit.setVisible(false);
		descrPSeparator.setVisible(false);
		bRemove.setVisible(false);
		
		descriptionPanel.add(lType);
		descriptionPanel.add(lDescr);
		descriptionPanel.add(lRegion);
		descriptionPanel.add(lRelative);
		descriptionPanel.add(lAbsolute);
		descriptionPanel.add(lAddress);
		descriptionPanel.add(lData);
		descriptionPanel.add(lContainer);
		descriptionPanel.add(wrapInJPanel(FlowLayout.CENTER, 3, 3, bEdit));
		descriptionPanel.add(descrPSeparator);
		descriptionPanel.add(Box.createVerticalGlue());
		descriptionPanel.add(wrapInJPanel(FlowLayout.CENTER, 3, 3, bRemove));
		
		descriptionPanel.setMinimumSize(new Dimension(100, descriptionPanel.getMinimumSize().height));
		
		updateDescriptionPanel = () ->
		{
			MDirectObject selObj = selectedObj;
			
			boolean notNull = selObj != null;
			boolean passedFilter = notNull && isExclusive(selObj.getContainer(), showFilter);
			
			if (notNull && passedFilter)
			{
				int type = 0xFFFF & selObj.getType();
				lType.setText("Type: 0x" + hexShort(type));
				lDescr.setText("Description: " + meta.getObjectTypeDesription((short) type));
				lRegion.setText("Region: (" + selObj.getRegionX() + ", " + selObj.getRegionY() + ")");
				lRelative.setText("Relative position: (x = " + selObj.getRelativeX() + ", y = " + selObj.getRelativeY() + ")");
				lAbsolute.setText("Absolute position: (x = " + selObj.getAbsoluteX() + ", y = " + selObj.getAbsoluteY() + ")");
				lAddress.setText("Allocation address: 0x" + Integer.toHexString(selObj.getAllocationAddress()));
				lData.setText("Additional data: " + byteArrToHexString(selObj.getData()));
				lContainer.setText("Container: " + selObj.getContainer());
				bEdit.setVisible(true);
				descrPSeparator.setVisible(true);
				bRemove.setVisible(true);
			}
			else
			{
				if (notNull)
				{
					selectedObj = null;
					imgPanel.repaint();
				}
				
				lType.setText("[No object selected]");
				lDescr.setText("");
				lRegion.setText("");
				lRelative.setText("");
				lAbsolute.setText("");
				lAddress.setText("");
				lData.setText("");
				lContainer.setText("");
				bEdit.setVisible(false);
				descrPSeparator.setVisible(false);
				bRemove.setVisible(false);
			}
			descriptionPanel.validate();
			descriptionPanel.repaint();
		};
		bEdit.addActionListener(e -> showEditDialog(selectedObj));
		bRemove.addActionListener(e ->
		{
			objectArr.remove(selectedObj);
			selectedObj = null;
			updateDescriptionPanel.run();
			imgPanel.repaint();
		});
		
		Runnable updateDescriptionPanelPosition = () ->
		{
			MDirectObject selObj = selectedObj;
			lRegion.setText("Region: (" + selObj.getRegionX() + ", " + selObj.getRegionY() + ")");
			lRelative.setText("Relative position: (x = " + selObj.getRelativeX() + ", y = " + selObj.getRelativeY() + ")");
			lAbsolute.setText("Absolute position: (x = " + selObj.getAbsoluteX() + ", y = " + selObj.getAbsoluteY() + ")");
			descriptionPanel.repaint();
		};
		
		JRadioButton rbShowAT = new JRadioButton("Show all tables");
		JRadioButton rbShowRT = new JRadioButton("Show region table");
		JRadioButton rbShowILT = new JRadioButton("Show initial table");
		JRadioButton rbHideAll = new JRadioButton("Hide all tables");
		
		Runnable updateFilter = () ->
		{
			MDirectObject.Container filter = null;
			if (rbShowAT.isSelected())
				filter = MDirectObject.Container.ALL_TABLES;
			else if (rbShowRT.isSelected())
				filter = MDirectObject.Container.REGION_TABLE;
			else if (rbShowILT.isSelected())
				filter = MDirectObject.Container.INITIAL_TABLE;
			else
				filter = null;
			
			showFilter = filter;
			MDirectObject selectedObj = this.selectedObj;
			
			if ((selectedObj != null) && !isExclusive(selectedObj.getContainer(), filter))
			{
				selectedObj = null;
				updateDescriptionPanel.run();
			}
			imgPanel.repaint();
		};
		
		rbShowAT.addActionListener(e -> updateFilter.run());
		rbShowRT.addActionListener(e -> updateFilter.run());
		rbShowILT.addActionListener(e -> updateFilter.run());
		rbHideAll.addActionListener(e -> updateFilter.run());
		
		ButtonGroup rbTableGroup = new ButtonGroup();
		rbTableGroup.add(rbShowAT);
		rbTableGroup.add(rbShowRT);
		rbTableGroup.add(rbShowILT);
		rbTableGroup.add(rbHideAll);
		
		rbShowAT.setSelected(true);
		
		descriptionPanel.add(Box.createVerticalGlue());
		descriptionPanel.add(createBoxCompatibleSeparator(-1, 2, JSeparator.HORIZONTAL));
		descriptionPanel.add(rbShowAT);
		descriptionPanel.add(rbShowRT);
		descriptionPanel.add(rbShowILT);
		descriptionPanel.add(rbHideAll);
		
		MouseAdapter selectionActions = new MouseAdapter()
		{
			private boolean isDraggable;
			private boolean isDragging;
			
			@Override
			public void mouseReleased(MouseEvent e)
			{
				if ((e.getButton() == MouseEvent.BUTTON1) && !isDragging)
				{
					try
					{
						AffineTransform tra = createTransform(imgPanel.getWidth(), imgPanel.getHeight());
						
						Point2D p = tra.inverseTransform(e.getPoint(), new Point2D.Double());
						boolean selectionFound = false;
						double threshold = selectionThreshold;
						for (MDirectObject obj:objectArr)
						{
							if (!isExclusive(obj.getContainer(), showFilter))
								continue;
							
							double dist = p.distance(obj.getAbsolutePosition());
							if (dist > threshold)
								continue;
							
							selectionFound = true;
							selectedObj = obj;
							threshold = dist;
						}
						if (!selectionFound)
							selectedObj = null;
						
						updateDescriptionPanel.run();
						imgPanel.paintImmediately(0, 0, imgPanel.getWidth(), imgPanel.getHeight());
					}
					catch (Exception ex)
					{
						ex.printStackTrace();
					}
				}
				isDragging = false;
			}
			@Override
			public void mousePressed(MouseEvent e)
			{
				try
				{
					MDirectObject selObj = selectedObj;
					if ((e.getButton() == MouseEvent.BUTTON1) && (selObj != null))
					{
						AffineTransform tra = createTransform(imgPanel.getWidth(), imgPanel.getHeight());
						
						Point2D p = tra.inverseTransform(e.getPoint(), new Point2D.Double());
						
						if (p.distance(selObj.getAbsolutePosition()) < selectionThreshold)
							isDraggable = true;
						else
							isDraggable = false;
					}
					else
						isDraggable = false;
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
					isDraggable = false;
				}
			}
			@Override
			public void mouseDragged(MouseEvent e)
			{
				try
				{
					if (isDraggable)
					{
						isDragging = true;
						
						AffineTransform tra = createTransform(imgPanel.getWidth(), imgPanel.getHeight());
						
						Point2D p = tra.inverseTransform(e.getPoint(), new Point2D.Double());
						int x = (int) p.getX();
						int y = (int) p.getY();
						if (x < 0)
							x = 0;
						if (y < 0)
							y = 0;
						selectedObj.setAbsolutePosition(x, y);
						
						updateDescriptionPanelPosition.run();
						imgPanel.paintImmediately(0, 0, imgPanel.getWidth(), imgPanel.getHeight());
					}
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
					isDragging = false;
					isDraggable = false;
				}
			}
		};
		
		imgPanel.addMouseListener(selectionActions);
		imgPanel.addMouseMotionListener(selectionActions);
		
		
	}
	private double defractionize(double d)
	{
		if (d > 0.9975 && d < 1.0025)
			d = 1;
		return d;
	}
	private double limit(double val, double min, double max)
	{
		return (val < min)?min:((val > max)?max:val);
	}
	private Point2D.Double point2D(Point p)
	{
		return new Point2D.Double(p.x, p.y);
	}
	
	private void showEditDialog(MDirectObject object)
	{
		showEditDialog((JFrame) SwingUtilities.getWindowAncestor(imgPanel),
				object);
	}
	public void showEditDialog(JFrame owner, MDirectObject object)
	{
		editedObject = object;
		editAddressChanged = false;
		
		if (editDialog == null)
		{
			editDialog = new JDialog(owner, true);
			editDialog.setLayout(new BorderLayout());
			editDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			editDialog.setTitle("Edit object");
			
			JPanel centerPanel = new JPanel();
			centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
			
			JPanel addressPanel = new JPanel();
			lAddressEdit = new JLabel("Allocation address: 0x0000");
			JButton bAddressEdit = new JButton("Edit");
			bAddressEdit.addActionListener(e -> showAddressEditDialog(editDialog, editedObject, () ->
			{
				editAddressChanged = true;
				lAddressEdit.setText("Allocation address: 0x" + hexShort(addrEditSelectedAddress));
			}));
			
			addressPanel.add(lAddressEdit);
			addressPanel.add(bAddressEdit);
			centerPanel.add(addressPanel);
			
			JPanel typePanel = new JPanel();
			JLabel lType = new JLabel("Type: ");
			tfTypeEdit = new JTextField(50);
			((AbstractDocument) tfTypeEdit.getDocument()).setDocumentFilter(new DocumentFilter()
			{
				@Override
				public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException
				{
					replace(fb, offset, 0, string, attr);
				}
				@Override
				public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException
				{
					text = filterHexString(text);
					Document d = fb.getDocument();
					
					String currStr = d.getText(0, d.getLength());
					String textWS = withoutSpaces(text);
					String currStrWS = withoutSpaces(currStr);
					
					int currLen = currStrWS.length();
					int textLen = textWS.length();
					int lengthWS = lengthWithoutSpaces(d.getText(offset, length));
					
					if (currLen + textLen - lengthWS <= 4)
						fb.replace(offset, length, text, attrs);
					else if (currLen - lengthWS < 4)
						fb.replace(offset, length, text.substring(0, indexWithSpaces(text, 4 - currLen + lengthWS)), attrs);
				}
			});
			
			typePanel.add(lType);
			typePanel.add(tfTypeEdit);
			centerPanel.add(typePanel);
			
			JPanel dataPanel = new JPanel();
			JLabel lData = new JLabel("Data: ");
			tfDataEdit = new JTextField(50);
			((AbstractDocument) tfDataEdit.getDocument()).setDocumentFilter(new DocumentFilter()
			{
				@Override
				public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException
				{
					replace(fb, offset, 0, string, attr);
				}
				@Override
				public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException
				{
					text = filterHexString(text);
					int len = editedObject.getDataLength() * 2;
					Document d = fb.getDocument();
					
					String currStr = d.getText(0, d.getLength());
					String textWS = withoutSpaces(text);
					String currStrWS = withoutSpaces(currStr);
					
					int currLen = currStrWS.length();
					int textLen = textWS.length();
					int lengthWS = lengthWithoutSpaces(d.getText(offset, length));
					
					if (currLen + textLen - lengthWS <= len)
						fb.replace(offset, length, text, attrs);
					else if (currLen - lengthWS < len)
						fb.replace(offset, length, text.substring(0, indexWithSpaces(text, len - currLen + lengthWS)), attrs);
				}
			});
			
			dataPanel.add(lData);
			dataPanel.add(tfDataEdit);
			centerPanel.add(dataPanel);
			
			JPanel containerPanel = new JPanel();
			JLabel lContainer = new JLabel("Container: ");
			cbContainerEdit = new JComboBox<>(MDirectObject.Container.values());
			
			containerPanel.add(lContainer);
			containerPanel.add(cbContainerEdit);
			centerPanel.add(containerPanel);
			
			editDialog.add(centerPanel, BorderLayout.CENTER);
			
			JPanel southPanel = new JPanel();
			JButton bSave = new JButton("Save");
			JButton bCancel = new JButton("Cancel");
			southPanel.add(bSave);
			southPanel.add(bCancel);
			
			bSave.addActionListener(e ->
			{
				String sType = tfTypeEdit.getText();
				if ((sType == null) || (sType.length() == 0))
					sType = "0";
				else
					sType = withoutSpaces(sType);
				short type = (short) Integer.parseInt(sType, 16);
				
				String data = tfDataEdit.getText();
				if (data == null || (data.length() == 0))
					data = "0";
				else
					data = withoutSpaces(data);
				byte[] dataArr = hexArray(data);
				
				if (type != editedObject.getType())
				{
					editedObject.setType(type);
					imgPanel.repaint();
				}
				editedObject.setData(Arrays.copyOf(dataArr, editedObject.getDataLength()));
				
				if (editAddressChanged)
					editedObject.setAllocationAddress(addrEditSelectedAddress);
				
				editedObject.setContainer((MDirectObject.Container) cbContainerEdit.getSelectedItem());
				
				editDialog.dispose();
				
				updateDescriptionPanel.run();
			});
			
			Runnable cancelAction = () ->
			{
				editDialog.dispose();
			};
			bCancel.addActionListener(e -> cancelAction.run());
			editDialog.addWindowListener(new WindowAdapter()
			{
				@Override
				public void windowClosing(WindowEvent e)
				{
					cancelAction.run();
				}
			});
			
			editDialog.add(southPanel, BorderLayout.SOUTH);
			editDialog.pack();
			editDialog.setResizable(false);
			editDialog.setLocationRelativeTo(owner);
		}
		lAddressEdit.setText("Allocation address: 0x" + hexShort(editedObject.getAllocationAddress()));
		tfTypeEdit.setText(hexShort(0xFFFF & editedObject.getType()));
		tfDataEdit.setText(byteArrToHexString(editedObject.getData()));
		cbContainerEdit.setSelectedItem(editedObject.getContainer());
		
		editDialog.setVisible(true);
	}
	private void showAddressEditDialog(JDialog owner, MDirectObject object, Runnable updateListener)
	{
		editedAddrObject = object;
		editAddrUpdateListener = updateListener;
		
		final int startAddr = 0xE140;
		final int endAddr = 0xFB80;
		final int blockSize = 0x40;
		
		if (addrEditDialog == null)
		{
			addrEditDialog = new JDialog(owner, true);
			addrEditDialog.setLayout(new BorderLayout());
			addrEditDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			addrEditDialog.setTitle("Edit Address");
		}
		
		JTable table = new JTable(new AbstractTableModel()
		{
			@Override
			public Object getValueAt(int rowIndex, int columnIndex)
			{
				return null;
			}
			@Override
			public int getRowCount()
			{
				return (endAddr - startAddr) / blockSize;
			}
			@Override
			public int getColumnCount()
			{
				return 1;
			}
			@Override
			public boolean isCellEditable(int rowIndex, int columnIndex)
			{
				return false;
			}
			@Override
			public String getColumnName(int column)
			{
				return "Available Memory Blocks";
			}
		});
		table.getTableHeader().setReorderingAllowed(false);
		
		HashMap<Integer, LinkedList<MDirectObject>> tableAddressMap = new HashMap<>();
		for (MDirectObject obj:objectArr)
		{
			if (!obj.equals(editedAddrObject))
			{
				int addr = 0xFFFF & obj.getAllocationAddress();
				LinkedList<MDirectObject> list = tableAddressMap.getOrDefault(addr, new LinkedList<>());
				list.add(obj);
				tableAddressMap.put(addr, list);
			}
		}
		
		table.setDefaultRenderer(Object.class, new TableCellRenderer()
		{
			private Object synchronizer = new Object();
			private ArrayList<JPanel> cells;
			private ArrayList<boolean[]> flags;
			private final Color selectionBorderColor = new Color(88, 88, 255);
			private final BasicStroke selectionBorderStroke = new BasicStroke(2f);
			private final LinkedList<MDirectObject> emptyList = new LinkedList<>();
			private final Color emptyBlockColor = new Color(192, 255, 192);
			private final Color usedBlockColor = new Color(200, 200, 200);
			private final Color textColor = new Color(0, 0, 0);
			private final Color seletedObjectTextColor = new Color(255, 48, 16);
			
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
			{
				synchronized (synchronizer)
				{
					if (cells == null)
					{
						cells = new ArrayList<>();
						flags = new ArrayList<>();
					}
					
					int cellsSize = cells.size();
					if (cellsSize > row)
					{
						JPanel cell = cells.get(row);
						if (cell != null)
						{
							boolean[] flagArr = flags.get(row);
							flagArr[0] = isSelected;
							flagArr[1] = hasFocus;
							return cell;
						}
					}

					JPanel cell = new JPanel()
					{
						@Override
						public void paint(Graphics g)
						{
							super.paint(g);
							Graphics2D g2d = (Graphics2D) g.create();
							g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
							
							int width = getWidth();
							int height = getHeight();
							int addr = startAddr + (row * blockSize);
							boolean[] flagArr = flags.get(row);
							
							String startAddrStr = "0x" + hexShort(addr);
							String endAddrStr = "0x" + hexShort(addr + 0x3F);
							String sampleObjText = "[0x0000]";
							Font font = getFont();
							FontRenderContext fontRC = g2d.getFontRenderContext();
							LineMetrics sASMetrics = font.getLineMetrics(startAddrStr, fontRC);
							LineMetrics eASMetrics = font.getLineMetrics(endAddrStr, fontRC);
							LineMetrics sOTMetrics = font.getLineMetrics(sampleObjText, fontRC);
							Rectangle2D sOTBounds = font.getStringBounds(sampleObjText, fontRC);
							int sOTWidth = (int) Math.ceil(sOTBounds.getWidth());
							int sOTHeight = (int) Math.ceil(sOTBounds.getHeight());
							int sOTAscend = (int) Math.ceil(sOTMetrics.getAscent());
							int sOTDescend = (int) Math.ceil(sOTMetrics.getDescent());
							int selectionOffset = flagArr[0]?2:0;
							
							int objTextX = width - sOTWidth - selectionOffset;
							int objTextY = sOTAscend + selectionOffset;
							final int objTextHorizontalMargin = 3;
							
							LinkedList<MDirectObject> list = tableAddressMap.getOrDefault(addr, emptyList);
							if (list.size() == 0)
							{
								g2d.setColor(emptyBlockColor);
								g2d.fillRect(0, 0, width, height);
							}
							else
							{
								g2d.setColor(usedBlockColor);
								g2d.fillRect(0, 0, width, height);
								
								g2d.setColor(textColor);
								for (MDirectObject obj: list)
								{
									g2d.drawString("[0x" + hexShort(0xFFFF & obj.getType()) + "]", objTextX, objTextY);
									objTextY += sOTHeight;
									if ((objTextY + sOTDescend + selectionOffset) > height)
									{
										objTextY = sOTAscend + selectionOffset;
										objTextX -= sOTWidth + objTextHorizontalMargin;
									}
								}
							}
							
							if (flagArr[0])
							{
								g2d.setColor(seletedObjectTextColor);
								g2d.drawString("[0x" + hexShort(0xFFFF & editedAddrObject.getType()) + "]", objTextX, objTextY);
							}
							
							g2d.setColor(textColor);
							g2d.drawString(startAddrStr, selectionOffset, sASMetrics.getAscent() + selectionOffset);
							g2d.drawString(endAddrStr, selectionOffset, height - eASMetrics.getDescent() - selectionOffset);
							
							if (flagArr[0])
							{
								g2d.setColor(selectionBorderColor);
								g2d.setStroke(selectionBorderStroke);
								Rectangle2D rect = new Rectangle2D.Double(0.75, 0.75, width - 1.65, height - 2);
								g2d.draw(rect);
							}
						}
					};
				
					if (cellsSize > row)
					{
						flags.set(row, new boolean[]{isSelected, hasFocus});
						cells.set(row, cell);
					}
					else
					{
						for (int i = cellsSize; i < row; i++)
						{
							flags.add(i, null);
							cells.add(i, null);
						}
						flags.add(row, new boolean[]{isSelected, hasFocus});
						cells.add(row, cell);
					}
					return cell;
				}
			}
		});
		table.setRowHeight(60);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		int initialSelection = ((0xFFFF & editedAddrObject.getAllocationAddress()) - startAddr) / blockSize;
		String initialSelectedAddressText = null;
		if ((initialSelection > 0) && initialSelection < (table.getRowCount()))
		{
			table.changeSelection(initialSelection, 0, false, false);
			initialSelectedAddressText = "Selected address: 0x"
					+ hexShort(0xFFFF & editedAddrObject.getAllocationAddress());
		}
		else
			initialSelectedAddressText = "No block selected";
		
		JScrollPane tSP = new JScrollPane(table);
		addrEditDialog.add(tSP, BorderLayout.CENTER);
		
		JPanel southPanel = new JPanel();
		southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
		
		JPanel selectedAddressPanel = new JPanel();
		
		JLabel lSelectedAddress = new JLabel(initialSelectedAddressText);
		
		table.getSelectionModel().addListSelectionListener(e ->
		{
			int row = table.getSelectedRow();
			if (row < 0)
				lSelectedAddress.setText("No block selected");
			else
				lSelectedAddress.setText("Selected block: 0x"
						+ hexShort(row * blockSize + startAddr));
		});
		
		selectedAddressPanel.add(lSelectedAddress);
		
		southPanel.add(selectedAddressPanel);
		southPanel.add(new JSeparator());
		
		JPanel buttonPanel = new JPanel();
		JButton bOk = new JButton("Ok");
		JButton bCancel = new JButton("Cancel");
		
		Runnable saveAction = () ->
		{
			int row = table.getSelectedRow();
			if (row < 0)
			{
				JOptionPane.showMessageDialog(addrEditDialog, "Select a memory address first.", "Edit Address", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			
			addrEditSelectedAddress = row * blockSize + startAddr;
			
			addrEditDialog.dispose();
			addrEditDialog.getContentPane().removeAll();
			editAddrUpdateListener.run();
		};
		bOk.addActionListener(e -> saveAction.run());
		
		Runnable cancelAction = () ->
		{
			addrEditDialog.dispose();
			addrEditDialog.getContentPane().removeAll();
		};
		bCancel.addActionListener(e -> cancelAction.run());
		addrEditDialog.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				cancelAction.run();
			}
		});
		
		buttonPanel.add(bOk);
		buttonPanel.add(bCancel);
		
		southPanel.add(buttonPanel);
		
		addrEditDialog.add(southPanel, BorderLayout.SOUTH);
		
		addrEditDialog.pack();
		addrEditDialog.setLocationRelativeTo(owner);
		addrEditDialog.setVisible(true);
	}
	
	public void showAddDialog(JFrame owner)
	{
		if (objectArr.size() > 256)
		{
			JOptionPane.showMessageDialog(owner,
					"Object limit reached. Consider removing some objects first.",
					"Add Object", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		int x = (int) Math.round(Math.max(xPos, 0));
		int y = (int) Math.round(Math.max(yPos, 0));
		
		addObject = new MDirectObject(x, y, 0, (short) 0, new byte[8], MDirectObject.Container.REGION_TABLE);
		addAddressSelected = false;
		if (addDialog == null)
		{
			addDialog = new JDialog(owner, true);
			addDialog.setLayout(new BorderLayout());
			addDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			addDialog.setTitle("Add object");
			
			JPanel centerPanel = new JPanel();
			centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
			
			JPanel addressPanel = new JPanel();
			lAddressAdd = new JLabel("Allocation address: [not selected]");
			JButton bAddressEdit = new JButton("Edit");
			bAddressEdit.addActionListener(e -> showAddressEditDialog(addDialog, addObject, () ->
			{
				addAddressSelected = true;
				lAddressAdd.setText("Allocation address: 0x" + hexShort(addrEditSelectedAddress));
			}));
			
			addressPanel.add(lAddressAdd);
			addressPanel.add(bAddressEdit);
			centerPanel.add(addressPanel);
			
			JPanel typePanel = new JPanel();
			JLabel lType = new JLabel("Type: ");
			tfTypeAdd = new JTextField(50);
			((AbstractDocument) tfTypeAdd.getDocument()).setDocumentFilter(new DocumentFilter()
			{
				@Override
				public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException
				{
					replace(fb, offset, 0, string, attr);
				}
				@Override
				public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException
				{
					text = filterHexString(text);
					Document d = fb.getDocument();
					
					String currStr = d.getText(0, d.getLength());
					String textWS = withoutSpaces(text);
					String currStrWS = withoutSpaces(currStr);
					
					int currLen = currStrWS.length();
					int textLen = textWS.length();
					int lengthWS = lengthWithoutSpaces(d.getText(offset, length));
					
					if (currLen + textLen - lengthWS <= 4)
						fb.replace(offset, length, text, attrs);
					else if (currLen - lengthWS < 4)
						fb.replace(offset, length, text.substring(0, indexWithSpaces(text, 4 - currLen + lengthWS)), attrs);
				}
			});
			
			typePanel.add(lType);
			typePanel.add(tfTypeAdd);
			centerPanel.add(typePanel);
			
			JPanel dataPanel = new JPanel();
			JLabel lData = new JLabel("Data: ");
			tfDataAdd = new JTextField(50);
			((AbstractDocument) tfDataAdd.getDocument()).setDocumentFilter(new DocumentFilter()
			{
				@Override
				public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException
				{
					replace(fb, offset, 0, string, attr);
				}
				@Override
				public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException
				{
					text = filterHexString(text);
					int len = addObject.getDataLength() * 2;
					Document d = fb.getDocument();
					
					String currStr = d.getText(0, d.getLength());
					String textWS = withoutSpaces(text);
					String currStrWS = withoutSpaces(currStr);
					
					int currLen = currStrWS.length();
					int textLen = textWS.length();
					int lengthWS = lengthWithoutSpaces(d.getText(offset, length));
					
					if (currLen + textLen - lengthWS <= len)
						fb.replace(offset, length, text, attrs);
					else if (currLen - lengthWS < len)
						fb.replace(offset, length, text.substring(0, indexWithSpaces(text, len - currLen + lengthWS)), attrs);
				}
			});
			
			dataPanel.add(lData);
			dataPanel.add(tfDataAdd);
			centerPanel.add(dataPanel);
			
			JPanel containerPanel = new JPanel();
			JLabel lContainer = new JLabel("Container: ");
			cbContainerAdd = new JComboBox<>(MDirectObject.Container.values());
			cbContainerAdd.setSelectedItem(MDirectObject.Container.REGION_TABLE);
			
			containerPanel.add(lContainer);
			containerPanel.add(cbContainerAdd);
			centerPanel.add(containerPanel);
			
			addDialog.add(centerPanel, BorderLayout.CENTER);
			
			JPanel southPanel = new JPanel();
			JButton bSave = new JButton("Save");
			JButton bCancel = new JButton("Cancel");
			southPanel.add(bSave);
			southPanel.add(bCancel);
			
			bSave.addActionListener(e ->
			{
				if (!addAddressSelected)
				{
					JOptionPane.showMessageDialog(addDialog, "No allocation address selected.", "Add object", JOptionPane.ERROR_MESSAGE);
					return;
				}
				
				String sType = tfTypeAdd.getText();
				if ((sType == null) || (sType.length() == 0))
					sType = "0";
				else
					sType = withoutSpaces(sType);
				short type = (short) Integer.parseInt(sType, 16);
				
				String data = tfDataAdd.getText();
				if (data == null || (data.length() == 0))
					data = "0";
				else
					data = withoutSpaces(data);
				byte[] dataArr = hexArray(data);
				
				if (type != addObject.getType())
					addObject.setType(type);
				
				addObject.setData(Arrays.copyOf(dataArr, addObject.getDataLength()));
				addObject.setAllocationAddress(addrEditSelectedAddress);
				addObject.setContainer((MDirectObject.Container) cbContainerAdd.getSelectedItem());
				
				objectArr.add(addObject);
				
				addDialog.dispose();
				imgPanel.repaint();
			});
			
			Runnable cancelAction = () ->
			{
				addDialog.dispose();
			};
			bCancel.addActionListener(e -> cancelAction.run());
			addDialog.addWindowListener(new WindowAdapter()
			{
				@Override
				public void windowClosing(WindowEvent e)
				{
					cancelAction.run();
				}
			});
			
			addDialog.add(southPanel, BorderLayout.SOUTH);
			addDialog.pack();
			addDialog.setResizable(false);
			addDialog.setLocationRelativeTo(owner);
		}
		lAddressAdd.setText("Allocation address: [not selected]");
		tfTypeAdd.setText(hexShort(0xFFFF & addObject.getType()));
		tfDataAdd.setText(byteArrToHexString(addObject.getData()));
		cbContainerAdd.setSelectedItem(addObject.getContainer());
		
		addDialog.setVisible(true);
	}
	
	private static boolean isExclusive(MDirectObject.Container base, MDirectObject.Container filter)
	{
		return (filter != null) &&
				(
					(filter == MDirectObject.Container.ALL_TABLES)
					|| (base == MDirectObject.Container.ALL_TABLES)
					|| (filter == base)
				);
	}
	private static String filterHexString(String s)
	{
		return s.chars()
			.map(c -> Character.toUpperCase(c))
			.filter(c ->
				(((c >= '0') && (c <= '9'))
					|| ((c >= 'A') && (c <= 'F'))
					|| Character.isWhitespace(c)))
			.collect(StringBuilder::new, (t, value) -> t.append((char) value), StringBuilder::append)
			.toString();
	}
	private static int lengthWithoutSpaces(String s)
	{
		return (int) s.chars().filter(c -> !Character.isWhitespace(c)).count();
	}
	private static String withoutSpaces(String s)
	{
		return s.chars()
				.filter(c -> !Character.isWhitespace(c))
				.collect(StringBuilder::new, (sb, c) -> sb.append((char) c), StringBuilder::append)
				.toString();
	}
	private static int indexWithSpaces(String str, int indexWithoutSpaces)
	{
		int counter = 0;
		char[] cArr = str.toCharArray();
		for (int i = 0; i < cArr.length; i++)
		{
			if (!Character.isWhitespace(cArr[i]))
			{
				if (counter == indexWithoutSpaces)
					return i;
				counter++;
			}
		}
		if (indexWithoutSpaces >= 0)
			return str.length();
		return -1;
	}
	private byte[] hexArray(String data)
	{
		char[] cArr = data.toCharArray();
		int l = cArr.length;
		byte[] ret = new byte[(int) Math.ceil(l / 2.0)];
		for (int i = 0; i < l; i += 2)
		{
			if (i + 1 < l)
			{
				char c1 = cArr[i];
				char c2 = cArr[i + 1];
				ret[i / 2] = (byte) ((Byte.parseByte("" + c1, 16) << 4) | Byte.parseByte("" + c2, 16));
			}
			else
			{
				ret[i / 2] = (byte) (Byte.parseByte("" + cArr[i], 16) << 4);
			}
		}
		return ret;
	}
	private AffineTransform createTransform(int panelWidth, int panelHeight)
	{
		return createTransform(panelWidth, panelHeight, scale);
	}
	private AffineTransform createTransform(int panelWidth, int panelHeight, double scale)
	{
		double pW = panelWidth / 2.0;
		double pH = panelHeight / 2.0;
		
		AffineTransform ret = new AffineTransform();
		ret.translate(pW, pH);
		ret.scale(scale, scale);
		ret.translate(-xPos, -yPos);
		
		return ret;
	}
	private void limitCoords(int w, int h)
	{
		if (xPos < 0)
			xPos = 0;
		else if (xPos > w)
			xPos = w;
		if (yPos < 0)
			yPos = 0;
		else if (yPos > h)
			yPos = h;
	}
	private static JSeparator createBoxCompatibleSeparator(int width, int height, int orientation)
	{
		JSeparator sep = new JSeparator(orientation);
		Dimension d = new Dimension((width < 0)?sep.getMaximumSize().width:width,
				(height < 0)?sep.getMaximumSize().height:height);
		sep.setPreferredSize(d);
		sep.setMaximumSize(d);
		sep.setOpaque(false);
		return sep;
	}
	private static JPanel wrapInJPanel(int alignment, int hgap, int vgap, Component... comp)
	{
		JPanel ret = new JPanel(new FlowLayout(alignment, hgap, vgap));
		int height = 0;
		for (int i = 0; i < comp.length; i++)
		{
			ret.add(comp[i]);
			Dimension dim = comp[i].getPreferredSize();
			if (height < dim.height)
				height = dim.height;
		}
		height += 2 * vgap;
		ret.setMaximumSize(new Dimension(ret.getMaximumSize().width, height));
		ret.setMinimumSize(new Dimension(ret.getMinimumSize().width, height));
		return ret;
	}
	private static String byteArrToHexString(byte[] arr)
	{
		if (arr == null)
			return "null";
		String ret = "";
		for (int i = 0; i < arr.length; i++)
			ret += hexByte(arr[i] & 0xFF) + " ";
		return ret;
	}
	private static String hexByte(int b)
	{
		String ret = Integer.toHexString(b);
		if (ret.length() < 2)
			return "0" + ret;
		return ret;
	}
	private static String hexShort(int b)
	{
		String ret = Integer.toHexString(b);
		switch (ret.length())
		{
			case 1:
				return "000" + ret;
			case 2:
				return "00" + ret;
			case 3:
				return "0" + ret;
			default:
				return ret;
		}
	}
	
	public JPanel getImagePanel()
	{
		return imgPanel;
	}
	public JPanel getDescriptionPanel()
	{
		return descriptionPanel;
	}
	public void updateDescriptionPanel()
	{
		if (updateDescriptionPanel != null)
			updateDescriptionPanel.run();
	}
	public MDirectObject getSelectedObject()
	{
		return selectedObj;
	}
	public double getScale()
	{
		return scale;
	}
	public double getMinScale()
	{
		return minScale;
	}
	public double getMaxScale()
	{
		return maxScale;
	}
	public BufferedImage getImage()
	{
		return img;
	}
	public ArrayList<MDirectObject> getObjects()
	{
		return objectArr;
	}
	
	public void setSelectedObject(MDirectObject obj)
	{
		selectedObj = obj;
		updateDescriptionPanel();
	}
	public void setScale(double scale)
	{
		this.scale = scale;
	}
	public void setMinScale(double minScale)
	{
		this.minScale = minScale;
	}
	public void setMaxScale(double maxScale)
	{
		this.maxScale = maxScale;
	}
	public void setImage(BufferedImage img)
	{
		this.img = img;
		limitCoords(img.getWidth(), img.getHeight());
	}
	public void setObjects(ArrayList<MDirectObject> objArr)
	{
		selectedObj = null;
		objectArr = objArr;
		updateDescriptionPanel();
		imgPanel.repaint();
	}
}
