/*
 * 
 * DIY Layout Creator (DIYLC). Copyright (c) 2009-2018 held jointly by the individual authors.
 * 
 * This file is part of DIYLC.
 * 
 * DIYLC is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * DIYLC is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with DIYLC. If not, see
 * <http://www.gnu.org/licenses/>.
 */
package org.diylc.swing.plugins.canvas;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.apache.log4j.Logger;
import org.diylc.appframework.miscutils.IConfigListener;
import org.diylc.appframework.miscutils.IConfigurationManager;
import org.diylc.appframework.miscutils.Utils;
import org.diylc.clipboard.ComponentTransferable;
import org.diylc.common.BadPositionException;
import org.diylc.common.EventType;
import org.diylc.common.IComponentTransformer;
import org.diylc.common.IPlugIn;
import org.diylc.common.IPlugInPort;
import org.diylc.core.ExpansionMode;
import org.diylc.core.IDIYComponent;
import org.diylc.core.Template;
import org.diylc.core.measures.Size;
import org.diylc.core.measures.SizeUnit;
import org.diylc.swing.ActionFactory;
import org.diylc.swing.ISwingUI;
import org.diylc.swing.gui.TranslatedMenu;
import org.diylc.swing.gui.TranslatedPopupMenu;
import org.diylc.swing.images.IconLoader;
import org.diylc.swing.plugins.file.ProjectDrawingProvider;
import org.diylc.swingframework.ruler.IRulerListener;
import org.diylc.swingframework.ruler.Ruler.InchSubdivision;
import org.diylc.swingframework.ruler.RulerScrollPane;

import com.guigarage.gestures.GestureMagnificationEvent;
import com.guigarage.gestures.GestureMagnificationListener;
import com.guigarage.gestures.GestureUtilities;
import com.guigarage.gestures.GesturesNotSupportedException;

public class CanvasPlugin implements IPlugIn, ClipboardOwner {

  private static final Logger LOG = Logger.getLogger(CanvasPlugin.class);

  private RulerScrollPane scrollPane;
  private CanvasPanel canvasPanel;
  private JPopupMenu popupMenu;
  private JMenu selectionMenu;
  private JMenu expandMenu;
  private JMenu transformMenu;
  private JMenu applyTemplateMenu;
  private JMenu lockMenu;
  private JMenu unlockMenu;

  private ActionFactory.CutAction cutAction;
  private ActionFactory.CopyAction copyAction;
  private ActionFactory.PasteAction pasteAction;
  private ActionFactory.DuplicateAction duplicateAction;
  private ActionFactory.EditSelectionAction editSelectionAction;
  private ActionFactory.DeleteSelectionAction deleteSelectionAction;
  private ActionFactory.SaveAsTemplateAction saveAsTemplateAction;
  private ActionFactory.SaveAsBlockAction saveAsBlockAction;
  private ActionFactory.GroupAction groupAction;
  private ActionFactory.UngroupAction ungroupAction;
  private ActionFactory.SendToBackAction sendToBackAction;
  private ActionFactory.BringToFrontAction bringToFrontAction;
  private ActionFactory.NudgeAction nudgeAction;
  private ActionFactory.ExpandSelectionAction expandSelectionAllAction;
  private ActionFactory.ExpandSelectionAction expandSelectionImmediateAction;
  private ActionFactory.ExpandSelectionAction expandSelectionSameTypeAction;
  private ActionFactory.RotateSelectionAction rotateClockwiseAction;
  private ActionFactory.RotateSelectionAction rotateCounterclockwiseAction;
  private ActionFactory.MirrorSelectionAction mirrorHorizontallyAction;
  private ActionFactory.MirrorSelectionAction mirrorVerticallyAction;
  private ActionFactory.FlexibleLeadsAction flexibleLeadsAction;

  private IPlugInPort plugInPort;
  private ISwingUI swingUI;

  private Clipboard clipboard;

  private double zoomLevel = 1;

  private IConfigurationManager<?> configManager;

  public CanvasPlugin(ISwingUI swingUI, IConfigurationManager<?> configManager) {
    this.swingUI = swingUI;
    this.configManager = configManager;
    clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
  }

  /**
   * Sets the scroll pane location to center and shows the contents. This method must be called when
   * the frame is initialized or the contents will remain hidden.
   * 
   * TODO: figure out a better way
   */
  public void scrollToCenterAndShowContents() {
    JScrollPane scroll = getScrollPane();
    Dimension totalSize = plugInPort.getCanvasDimensions(true, true);
    Dimension visibleSize = scroll.getSize();
    scroll.getHorizontalScrollBar().setValue((totalSize.width - visibleSize.width) / 2);
    scroll.getVerticalScrollBar().setValue((totalSize.height - visibleSize.height) / 2);
    getScrollPane().getViewport().setVisible(true);
  }

  @Override
  public void connect(IPlugInPort plugInPort) {
    this.plugInPort = plugInPort;
    try {
      swingUI.injectGUIComponent(getScrollPane(), SwingConstants.CENTER, false);
    } catch (BadPositionException e) {
      LOG.error("Could not install canvas plugin", e);
    }

    getScrollPane().setRulerVisible(
        configManager.readBoolean(IPlugInPort.SHOW_RULERS_KEY, true));

    // revalidate canvas on scrolling when we render visible rect only
    if (CanvasPanel.RENDER_VISIBLE_RECT_ONLY) {
      getScrollPane().getHorizontalScrollBar().addAdjustmentListener(new AdjustmentListener() {

        @Override
        public void adjustmentValueChanged(AdjustmentEvent e) {
          getCanvasPanel().invalidateCache();
          getCanvasPanel().revalidate();
        }
      });

      getScrollPane().getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {

        @Override
        public void adjustmentValueChanged(AdjustmentEvent e) {
          getCanvasPanel().invalidateCache();
          getCanvasPanel().revalidate();
        }
      });
    }

    configManager.addConfigListener(IPlugInPort.SHOW_RULERS_KEY,
        new IConfigListener() {

          @Override
          public void valueChanged(String key, Object value) {
            if (IPlugInPort.SHOW_RULERS_KEY.equals(key))
              getScrollPane().setRulerVisible((Boolean) value);
          }
        });

    configManager.addConfigListener(IPlugInPort.HARDWARE_ACCELERATION,
        new IConfigListener() {

          @Override
          public void valueChanged(String key, Object value) {
            canvasPanel.setUseHardwareAcceleration((Boolean) value);
            scrollPane.setUseHardwareAcceleration((Boolean) value);
          }
        });

    configManager.addConfigListener(IPlugInPort.METRIC_KEY,
        new IConfigListener() {

          @Override
          public void valueChanged(String key, Object value) {
            updateZeroLocation();
          }
        });

    configManager.addConfigListener(IPlugInPort.EXTRA_SPACE_KEY,
        new IConfigListener() {

          @Override
          public void valueChanged(String key, Object value) {
            refreshSize();
            // Scroll to the center.
            Rectangle visibleRect = canvasPanel.getVisibleRect();
            visibleRect.setLocation((canvasPanel.getWidth() - visibleRect.width) / 2,
                (canvasPanel.getHeight() - visibleRect.height) / 2);
            canvasPanel.scrollRectToVisible(visibleRect);
            canvasPanel.revalidate();

            updateZeroLocation();
          }
        });

    configManager.addConfigListener(IPlugInPort.RULER_IN_SUBDIVISION_KEY,
        new IConfigListener() {

          @Override
          public void valueChanged(String key, Object value) {
            getScrollPane().setInSubdivision(IPlugInPort.RULER_IN_SUBDIVISION_10
                .equalsIgnoreCase(value == null ? null : value.toString()) ? InchSubdivision.BASE_10
                    : InchSubdivision.BASE_2);
          }
        });

    configManager.addConfigListener(IPlugInPort.HIGHLIGHT_CONTINUITY_AREA,
        new IConfigListener() {

          @Override
          public void valueChanged(String key, Object value) {
            canvasPanel.repaint();
          }
        });

    getScrollPane().getViewport().setVisible(false);
  }

  public CanvasPanel getCanvasPanel() {
    if (canvasPanel == null) {
      canvasPanel = new CanvasPanel(plugInPort, configManager);
      canvasPanel.addMouseListener(new MouseAdapter() {

        private MouseEvent pressedEvent;

        @Override
        public void mouseClicked(MouseEvent e) {
          if (scrollPane.isMouseScrollMode() || e.getButton() == MouseEvent.BUTTON2)
            return;
          
          // do not pass isMetaDown on mac when button3 (two finger click) is pressed
          boolean ctrlDown = Utils.isMac() ? (e.getButton() == MouseEvent.BUTTON3 ? false : e.isMetaDown()): e.isControlDown();
          
          plugInPort.mouseClicked(e.getPoint(), e.getButton(),
              ctrlDown, e.isShiftDown(), e.isAltDown(), e.getClickCount());
        }

        @Override
        public void mousePressed(MouseEvent e) {
          LOG.info("Pressed: " + e.isPopupTrigger());
          canvasPanel.requestFocus();
          pressedEvent = e;
        }

        @Override
        public void mouseReleased(final MouseEvent e) {
          // Invoke the rest of the code later so we get the chance to
          // process selection messages.
          SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
              if (plugInPort.getNewComponentTypeSlot() == null && (e.isPopupTrigger()
                  || (pressedEvent != null && pressedEvent.isPopupTrigger()))) {
                // Enable actions.
                boolean enabled = !plugInPort.getSelectedComponents().isEmpty();
                getCutAction().setEnabled(enabled);
                getCopyAction().setEnabled(enabled);
                getDuplicateAction().setEnabled(enabled);
                try {
                  getPasteAction().setEnabled(
                      clipboard.isDataFlavorAvailable(ComponentTransferable.listFlavor));
                } catch (Exception ex) {
                  getPasteAction().setEnabled(false);
                }
                getEditSelectionAction().setEnabled(enabled);
                getDeleteSelectionAction().setEnabled(enabled);
                getExpandSelectionAllAction().setEnabled(enabled);
                getExpandSelectionImmediateAction().setEnabled(enabled);
                getExpandSelectionSameTypeAction().setEnabled(enabled);
                getGroupAction().setEnabled(enabled);
                getUngroupAction().setEnabled(enabled);
                getNudgeAction().setEnabled(enabled);
                getSendToBackAction().setEnabled(enabled);
                getBringToFrontAction().setEnabled(enabled);
                getRotateClockwiseAction().setEnabled(enabled);
                getRotateCounterclockwiseAction().setEnabled(enabled);
                getMirrorHorizontallyAction().setEnabled(enabled);
                getMirrorVerticallyAction().setEnabled(enabled);
                getFlexibleLeadsAction().setEnabled(enabled);

                getSaveAsTemplateAction()
                    .setEnabled(plugInPort.getSelectedComponents().size() == 1);
                getSaveAsBlockAction().setEnabled(plugInPort.getSelectedComponents().size() > 1);

                showPopupAt(e.getX(), e.getY());
              }
            }
          });
        }
      });           

      canvasPanel.addKeyListener(new KeyAdapter() {
        
        @Override
        public void keyReleased(KeyEvent e) {
          canvasPanel.setCursor(plugInPort.getCursorAt(canvasPanel.getMousePosition(), false, false, false));
        }

        @Override
        public void keyPressed(KeyEvent e) {      
          canvasPanel.setCursor(plugInPort.getCursorAt(canvasPanel.getMousePosition(), Utils.isMac() ? e.isMetaDown() : e.isControlDown(),
              e.isShiftDown(), e.isAltDown()));
          if (plugInPort.keyPressed(e.getKeyCode(),
              Utils.isMac() ? e.isMetaDown() : e.isControlDown(), e.isShiftDown(), e.isAltDown())) {
            e.consume();
          }
        }
      });

      canvasPanel.addMouseMotionListener(new MouseAdapter() {

        @Override
        public void mouseMoved(MouseEvent e) {
          if (scrollPane.isMouseScrollMode())
            return;
          canvasPanel.setCursor(plugInPort.getCursorAt(e.getPoint(), Utils.isMac() ? e.isMetaDown() : e.isControlDown(),
              e.isShiftDown(), e.isAltDown()));
          plugInPort.mouseMoved(e.getPoint(), Utils.isMac() ? e.isMetaDown() : e.isControlDown(),
              e.isShiftDown(), e.isAltDown());
        }
      });
      
      canvasPanel.getActionMap().put("zoomIn", new AbstractAction() {

        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e) {
          LOG.debug("Keyboard zoom-in triggered");
          zoom(-1);
        }
      });

      canvasPanel.getActionMap().put("zoomOut", new AbstractAction() {

        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e) {
          LOG.debug("Keyboard zoom-out triggered");
          zoom(1);
        }
      });
      
      if (!GestureUtilities.isSupported()) {
        LOG.info("Gestures are not supported, skipping initialization");
      }    
      try {
        GestureUtilities.registerListener(canvasPanel, new GestureMagnificationListener() {
          
          private long prevEventTime = 0;
          private static final int DELAY = 100;
          
          @Override
          public void magnify(GestureMagnificationEvent e) {
            if (System.currentTimeMillis() - this.prevEventTime < DELAY)
              return;
            
            if (e.getMagnification() > 0)
              CanvasPlugin.this.zoom(-1);
            else if (e.getMagnification() < 0)
              CanvasPlugin.this.zoom(1);
              
            prevEventTime = System.currentTimeMillis();
          }
        });
        LOG.info("Magnification gesture listener initialized.");
      } catch (GesturesNotSupportedException e) {
        LOG.error("Error registering gesture listener", e);
      }
    }
    return canvasPanel;
  }

  private RulerScrollPane getScrollPane() {
    if (scrollPane == null) {
      String subdivision = configManager.readString(
          IPlugInPort.RULER_IN_SUBDIVISION_KEY, IPlugInPort.RULER_IN_SUBDIVISION_DEFAULT);

      scrollPane = new RulerScrollPane(getCanvasPanel(),
          new ProjectDrawingProvider(plugInPort, true, false, true),
          new Size(1d, SizeUnit.cm).convertToPixels(), new Size(1d, SizeUnit.in).convertToPixels(),
          IPlugInPort.RULER_IN_SUBDIVISION_10.equalsIgnoreCase(subdivision)
              ? InchSubdivision.BASE_10
              : InchSubdivision.BASE_2);
      boolean metric = configManager.readBoolean(IPlugInPort.METRIC_KEY, true);

      boolean useHardwareAcceleration =
          configManager.readBoolean(IPlugInPort.HARDWARE_ACCELERATION, false);
      scrollPane.setUseHardwareAcceleration(useHardwareAcceleration);
      scrollPane.setMetric(metric);
      scrollPane.setWheelScrollingEnabled(true);
      scrollPane.addUnitListener(new IRulerListener() {

        @Override
        public void unitsChanged(boolean isMetric) {
          plugInPort.setMetric(isMetric);
        }
      });

      double extraSpace = plugInPort.getExtraSpace();
      scrollPane.setZeroLocation(new Point2D.Double(extraSpace, extraSpace));

      // disable built-in scrolling mechanism, we'll do it manually
      scrollPane.setWheelScrollingEnabled(false);

      scrollPane.addMouseWheelListener(new MouseWheelListener() {

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
          final JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();
          final JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();

          boolean wheelZoom =
              configManager.readBoolean(IPlugInPort.WHEEL_ZOOM_KEY, false);

          if (wheelZoom || (Utils.isMac() ? e.isMetaDown() : e.isControlDown())) {
            CanvasPlugin.this.zoom(e.getWheelRotation());            
          }
          if (e.isShiftDown()) {
            int iScrollAmount = e.getScrollAmount();
            int iNewValue = horizontalScrollBar.getValue()
                + horizontalScrollBar.getBlockIncrement() * iScrollAmount * e.getWheelRotation();
            if (iNewValue <= horizontalScrollBar.getMaximum())
              horizontalScrollBar.setValue(iNewValue);
          } else {
            int iScrollAmount = e.getScrollAmount();
            int iNewValue = verticalScrollBar.getValue()
                + verticalScrollBar.getBlockIncrement() * iScrollAmount * e.getWheelRotation();
            if (iNewValue <= verticalScrollBar.getMaximum())
              verticalScrollBar.setValue(iNewValue);
          }
        }
      });
    }
    return scrollPane;
  }
  
  public void zoom(int direction) {    
    Point mousePos = canvasPanel.getMousePosition(true);    

    // change zoom level
    double oldZoom = plugInPort.getZoomLevel();
    double newZoom;
    Double[] availableZoomLevels = plugInPort.getAvailableZoomLevels();
    if (direction > 0) {
      int i = availableZoomLevels.length - 1;
      while (i > 0 && availableZoomLevels[i] >= oldZoom) {
        i--;
      }
      plugInPort.setZoomLevel(newZoom = availableZoomLevels[i]);
    } else {
      int i = 0;
      while (i < availableZoomLevels.length - 1 && availableZoomLevels[i] <= oldZoom) {
        i++;
      }
      plugInPort.setZoomLevel(newZoom = availableZoomLevels[i]);
    }

    Rectangle2D selectionBounds = plugInPort.getSelectionBounds(true);
    Rectangle visibleRect = scrollPane.getVisibleRect();

    JScrollBar horizontal = scrollPane.getHorizontalScrollBar();
    JScrollBar vertical = scrollPane.getVerticalScrollBar();
    
    if (mousePos == null)
      mousePos = new Point((int)visibleRect.getCenterX(), (int)visibleRect.getCenterY());

    if (selectionBounds == null) {
      // center to cursor
      Point desiredPos = new Point((int) (1d * mousePos.x / oldZoom * newZoom),
          (int) (1d * mousePos.y / oldZoom * newZoom));
      int dx = desiredPos.x - mousePos.x;
      int dy = desiredPos.y - mousePos.y;
      horizontal.setValue(horizontal.getValue() + dx);
      vertical.setValue(vertical.getValue() + dy);
    } else {
      // center to selection
      horizontal.setValue((int) (selectionBounds.getX() + selectionBounds.getWidth() / 2
          - visibleRect.getWidth() / 2));
      vertical.setValue((int) (selectionBounds.getY() + selectionBounds.getHeight() / 2
          - visibleRect.getHeight() / 2));
    }
  }

  private void showPopupAt(int x, int y) {
    updateSelectionMenu(x, y);
    updateApplyTemplateMenu();
    updateLock(x, y);
    getPopupMenu().show(canvasPanel, x, y);
  }

  public JPopupMenu getPopupMenu() {
    if (popupMenu == null) {
      popupMenu = new TranslatedPopupMenu();
      popupMenu.add(getSelectionMenu());
      popupMenu.addSeparator();
      popupMenu.add(getCutAction());
      popupMenu.add(getCopyAction());
      popupMenu.add(getPasteAction());
      popupMenu.add(getDuplicateAction());
      popupMenu.addSeparator();
      popupMenu.add(getEditSelectionAction());
      popupMenu.add(getDeleteSelectionAction());
      popupMenu.add(getTransformMenu());
      popupMenu.add(getSaveAsTemplateAction());
      popupMenu.add(getApplyTemplateMenu());
      popupMenu.add(getSaveAsBlockAction());
      popupMenu.add(getExpandMenu());
      popupMenu.addSeparator();
      popupMenu.add(getLockMenu());
      popupMenu.add(getUnlockMenu());
      popupMenu.addSeparator();
      popupMenu.add(getFlexibleLeadsAction());
      popupMenu.addSeparator();
      popupMenu.add(ActionFactory.getInstance().createEditProjectAction(plugInPort));
    }
    return popupMenu;
  }

  public JMenu getSelectionMenu() {
    if (selectionMenu == null) {
      selectionMenu = new TranslatedMenu("Select");
      selectionMenu.setIcon(IconLoader.ElementsSelection.getIcon());
    }
    return selectionMenu;
  }

  public JMenu getExpandMenu() {
    if (expandMenu == null) {
      expandMenu = new TranslatedMenu("Expand Selection");
      expandMenu.setIcon(IconLoader.BranchAdd.getIcon());
      expandMenu.add(getExpandSelectionAllAction());
      expandMenu.add(getExpandSelectionImmediateAction());
      expandMenu.add(getExpandSelectionSameTypeAction());
    }
    return expandMenu;
  }

  public JMenu getLockMenu() {
    if (lockMenu == null) {
      lockMenu = new TranslatedMenu("Lock");
      lockMenu.setIcon(IconLoader.Lock.getIcon());
    }
    return lockMenu;
  }

  public JMenu getUnlockMenu() {
    if (unlockMenu == null) {
      unlockMenu = new TranslatedMenu("Unlock");
      unlockMenu.setIcon(IconLoader.Unlock.getIcon());
    }
    return unlockMenu;
  }

  public JMenu getTransformMenu() {
    if (transformMenu == null) {
      transformMenu = new TranslatedMenu("Transform Selection");
      transformMenu.setIcon(IconLoader.MagicWand.getIcon());
      transformMenu.add(getRotateClockwiseAction());
      transformMenu.add(getRotateCounterclockwiseAction());
      transformMenu.addSeparator();
      transformMenu.add(getMirrorHorizontallyAction());
      transformMenu.add(getMirrorVerticallyAction());
      transformMenu.addSeparator();
      transformMenu.add(getNudgeAction());
      transformMenu.addSeparator();
      transformMenu.add(getSendToBackAction());
      transformMenu.add(getBringToFrontAction());
      transformMenu.addSeparator();
      transformMenu.add(getGroupAction());
      transformMenu.add(getUngroupAction());
    }
    return transformMenu;
  }

  public JMenu getApplyTemplateMenu() {
    if (applyTemplateMenu == null) {
      applyTemplateMenu = new TranslatedMenu("Apply Variant");
      applyTemplateMenu.setIcon(IconLoader.BriefcaseInto.getIcon());
    }
    return applyTemplateMenu;
  }

  private void updateSelectionMenu(int x, int y) {
    getSelectionMenu().removeAll();
    for (IDIYComponent<?> component : plugInPort.findComponentsAt(new Point(x, y), false)) {
      JMenuItem item = new JMenuItem(component.getName());
      final IDIYComponent<?> finalComponent = component;
      item.addActionListener(new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent e) {
          List<IDIYComponent<?>> newSelection = new ArrayList<IDIYComponent<?>>();
          newSelection.add(finalComponent);
          plugInPort.updateSelection(newSelection);
          plugInPort.refresh();
        }
      });
      getSelectionMenu().add(item);
    }
  }

  private void updateLock(int x, int y) {
    getLockMenu().removeAll();
    getUnlockMenu().removeAll();
    List<IDIYComponent<?>> componentsAt = plugInPort.findComponentsAt(new Point(x, y), true);
    if (componentsAt.size() == 0) {
      getLockMenu().setEnabled(false);
      getUnlockMenu().setEnabled(false);
    } else {
      boolean hasLocked = false;
      boolean hasUnlocked = false;
      for (IDIYComponent<?> c : componentsAt) {
        if (plugInPort.getCurrentProject().getLockedComponents().contains(c)) {
          getUnlockMenu().add(new LockAction(c, false));
          hasLocked = true;
        } else {
          getLockMenu().add(new LockAction(c, true));
          hasUnlocked = true;
        }
      }
      getLockMenu().setEnabled(hasUnlocked);
      getUnlockMenu().setEnabled(hasLocked);
    }
  }

  private void updateApplyTemplateMenu() {
    getApplyTemplateMenu().removeAll();
    List<Template> templates = null;

    try {
      templates = plugInPort.getVariantsForSelection();
    } catch (Exception e) {
      LOG.info("Could not load variants for selection");
      getApplyTemplateMenu().setEnabled(false);
    }

    if (templates == null) {
      getApplyTemplateMenu().setEnabled(false);
      return;
    }

    getApplyTemplateMenu().setEnabled(templates.size() > 0);

    for (Template template : templates) {
      JMenuItem item = new JMenuItem(template.getName());
      final Template finalTemplate = template;
      item.addActionListener(new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent e) {
          plugInPort.applyVariantToSelection(finalTemplate);
        }
      });
      getApplyTemplateMenu().add(item);
    }
  }

  public ActionFactory.CutAction getCutAction() {
    if (cutAction == null) {
      cutAction = ActionFactory.getInstance().createCutAction(plugInPort, clipboard, this);
    }
    return cutAction;
  }

  public ActionFactory.CopyAction getCopyAction() {
    if (copyAction == null) {
      copyAction = ActionFactory.getInstance().createCopyAction(plugInPort, clipboard, this);
    }
    return copyAction;
  }

  public ActionFactory.PasteAction getPasteAction() {
    if (pasteAction == null) {
      pasteAction = ActionFactory.getInstance().createPasteAction(plugInPort, clipboard);
    }
    return pasteAction;
  }

  public ActionFactory.DuplicateAction getDuplicateAction() {
    if (duplicateAction == null) {
      duplicateAction = ActionFactory.getInstance().createDuplicateAction(plugInPort);
    }
    return duplicateAction;
  }

  public ActionFactory.EditSelectionAction getEditSelectionAction() {
    if (editSelectionAction == null) {
      editSelectionAction = ActionFactory.getInstance().createEditSelectionAction(plugInPort);
    }
    return editSelectionAction;
  }

  public ActionFactory.DeleteSelectionAction getDeleteSelectionAction() {
    if (deleteSelectionAction == null) {
      deleteSelectionAction = ActionFactory.getInstance().createDeleteSelectionAction(plugInPort);
    }
    return deleteSelectionAction;
  }

  public ActionFactory.RotateSelectionAction getRotateClockwiseAction() {
    if (rotateClockwiseAction == null) {
      rotateClockwiseAction =
          ActionFactory.getInstance().createRotateSelectionAction(plugInPort, 1);
    }
    return rotateClockwiseAction;
  }

  public ActionFactory.RotateSelectionAction getRotateCounterclockwiseAction() {
    if (rotateCounterclockwiseAction == null) {
      rotateCounterclockwiseAction =
          ActionFactory.getInstance().createRotateSelectionAction(plugInPort, -1);
    }
    return rotateCounterclockwiseAction;
  }

  public ActionFactory.MirrorSelectionAction getMirrorHorizontallyAction() {
    if (mirrorHorizontallyAction == null) {
      mirrorHorizontallyAction = ActionFactory.getInstance().createMirrorSelectionAction(plugInPort,
          IComponentTransformer.HORIZONTAL);
    }
    return mirrorHorizontallyAction;
  }

  public ActionFactory.MirrorSelectionAction getMirrorVerticallyAction() {
    if (mirrorVerticallyAction == null) {
      mirrorVerticallyAction = ActionFactory.getInstance().createMirrorSelectionAction(plugInPort,
          IComponentTransformer.VERTICAL);
    }
    return mirrorVerticallyAction;
  }

  public ActionFactory.FlexibleLeadsAction getFlexibleLeadsAction() {
    if (flexibleLeadsAction == null)
      flexibleLeadsAction = ActionFactory.getInstance().createFlexibleLeadsAction(plugInPort);
    return flexibleLeadsAction;
  }

  public ActionFactory.SaveAsTemplateAction getSaveAsTemplateAction() {
    if (saveAsTemplateAction == null) {
      saveAsTemplateAction = ActionFactory.getInstance().createSaveAsTemplateAction(plugInPort);
    }
    return saveAsTemplateAction;
  }

  public ActionFactory.SaveAsBlockAction getSaveAsBlockAction() {
    if (saveAsBlockAction == null) {
      saveAsBlockAction = ActionFactory.getInstance().createSaveAsBlockAction(plugInPort);
    }
    return saveAsBlockAction;
  }

  public ActionFactory.GroupAction getGroupAction() {
    if (groupAction == null) {
      groupAction = ActionFactory.getInstance().createGroupAction(plugInPort);
    }
    return groupAction;
  }

  public ActionFactory.UngroupAction getUngroupAction() {
    if (ungroupAction == null) {
      ungroupAction = ActionFactory.getInstance().createUngroupAction(plugInPort);
    }
    return ungroupAction;
  }

  public ActionFactory.SendToBackAction getSendToBackAction() {
    if (sendToBackAction == null) {
      sendToBackAction = ActionFactory.getInstance().createSendToBackAction(plugInPort);
    }
    return sendToBackAction;
  }

  public ActionFactory.BringToFrontAction getBringToFrontAction() {
    if (bringToFrontAction == null) {
      bringToFrontAction = ActionFactory.getInstance().createBringToFrontAction(plugInPort);
    }
    return bringToFrontAction;
  }

  public ActionFactory.NudgeAction getNudgeAction() {
    if (nudgeAction == null) {
      nudgeAction = ActionFactory.getInstance().createNudgeAction(plugInPort);
    }
    return nudgeAction;
  }

  public ActionFactory.ExpandSelectionAction getExpandSelectionAllAction() {
    if (expandSelectionAllAction == null) {
      expandSelectionAllAction =
          ActionFactory.getInstance().createExpandSelectionAction(plugInPort, ExpansionMode.ALL);
    }
    return expandSelectionAllAction;
  }

  public ActionFactory.ExpandSelectionAction getExpandSelectionImmediateAction() {
    if (expandSelectionImmediateAction == null) {
      expandSelectionImmediateAction = ActionFactory.getInstance()
          .createExpandSelectionAction(plugInPort, ExpansionMode.IMMEDIATE);
    }
    return expandSelectionImmediateAction;
  }

  public ActionFactory.ExpandSelectionAction getExpandSelectionSameTypeAction() {
    if (expandSelectionSameTypeAction == null) {
      expandSelectionSameTypeAction = ActionFactory.getInstance()
          .createExpandSelectionAction(plugInPort, ExpansionMode.SAME_TYPE);
    }
    return expandSelectionSameTypeAction;
  }

  @Override
  public EnumSet<EventType> getSubscribedEventTypes() {
    return EnumSet.of(EventType.PROJECT_LOADED, EventType.ZOOM_CHANGED, EventType.REPAINT,
        EventType.SCROLL_TO);
  }

  @SuppressWarnings("incomplete-switch")
  @Override
  public void processMessage(final EventType eventType, Object... params) {
    switch (eventType) {
      case PROJECT_LOADED:
        refreshSize();
        if ((Boolean) params[1]) {
          // Scroll to the center.
          final Rectangle visibleRect = canvasPanel.getVisibleRect();
          visibleRect.setLocation((canvasPanel.getWidth() - visibleRect.width) / 2,
              (canvasPanel.getHeight() - visibleRect.height) / 2);
          SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
              canvasPanel.scrollRectToVisible(visibleRect);
              canvasPanel.revalidate();
            }
          });
        }
        break;
      case ZOOM_CHANGED:
        final Rectangle visibleRect = canvasPanel.getVisibleRect();
        refreshSize();
        // Try to set the visible area to be centered with the previous
        // one.
        double zoomFactor = (Double) params[0] / zoomLevel;
        visibleRect.setBounds((int) (visibleRect.x * zoomFactor),
            (int) (visibleRect.y * zoomFactor), visibleRect.width, visibleRect.height);

        canvasPanel.scrollRectToVisible(visibleRect);
        canvasPanel.revalidate();

        updateZeroLocation();

        zoomLevel = (Double) params[0];
        break;
      case REPAINT:
        canvasPanel.repaint();
        // Refresh selection bounds after we're done with painting to ensure we have traced the
        // component areas
        SwingUtilities.invokeLater(new Runnable() {

          @Override
          public void run() {
            if (configManager.readBoolean(IPlugInPort.SHOW_RULERS_KEY, true))
              scrollPane.setSelectionRectangle(plugInPort.getSelectionBounds(true));
          }
        });
        break;
      case SCROLL_TO:
        Rectangle visibleRect2 = canvasPanel.getVisibleRect();
        Rectangle2D targetRect = (Rectangle2D) params[0];
        if (targetRect != null)
          canvasPanel.scrollRectToVisible(
              new Rectangle((int) (targetRect.getCenterX() - visibleRect2.width / 2),
                  (int) (targetRect.getCenterY() - visibleRect2.height / 2), visibleRect2.width,
                  visibleRect2.height));
        break;
    }
    // }
    // });
  }

  private void refreshSize() {
    Dimension d = plugInPort.getCanvasDimensions(true,
        configManager.readBoolean(IPlugInPort.EXTRA_SPACE_KEY, true));
    canvasPanel.setSize(d);
    canvasPanel.setPreferredSize(d);
    getScrollPane().setZoomLevel(plugInPort.getZoomLevel());
  }

  /**
   * Causes ruler scroll pane to refresh by sending a fake mouse moved message to the canvasPanel.
   */
  public void refresh() {
    MouseEvent event =
        new MouseEvent(canvasPanel, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 1, 1, // canvasPanel.getWidth()
                                                                                                 // /
            // 2,
            // canvasPanel.getHeight() / 2,
            0, false);
    canvasPanel.dispatchEvent(event);
  }

  @Override
  public void lostOwnership(Clipboard clipboard, Transferable contents) {
    // TODO Auto-generated method stub

  }

  private void updateZeroLocation() {
    double extraSpace = CanvasPlugin.this.plugInPort.getExtraSpace();
    getScrollPane().setZeroLocation(new Point2D.Double(extraSpace, extraSpace));
  }
  
  class LockAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private IDIYComponent<?> component;
    private boolean locked;

    public LockAction(IDIYComponent<?> component, boolean locked) {
      super();
      this.locked = locked;
      this.component = component;
      putValue(AbstractAction.NAME, component.getName());      
    }

    @Override
    public void actionPerformed(ActionEvent e) {
     plugInPort.lockComponent(component, locked); 
    } 
  
  }
}
