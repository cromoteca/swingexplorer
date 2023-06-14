package org.swingexplorer.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;

public class ActExportToDataset extends RichAction {

    private final PnlComponentTree pnlComponentTree;
//    private final Application application;

    private final ObjectMapper objectMapper = new ObjectMapper();

    ActExportToDataset(/*Application application,*/PnlComponentTree pnlComponentTree) {
        setName("Export to Dataset");
        setTooltip("Export screenshot and component positions");
        setIcon("display.png");

//        this.application = application;
        this.pnlComponentTree = pnlComponentTree;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Component selected = pnlComponentTree.getSelectedComponent();
        try {
            if (selected == null) {
                throw new DisplayableException("There is no component selected");
            }

            File dir = new File("vaadin-dataset");
            dir.mkdir();
            long timestamp = System.currentTimeMillis();

            takeScreenshot(new File(dir, timestamp + ".png"), selected);

            ArrayNode json = objectMapper.createArrayNode();
            logComponents(json, selected, null, null);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(dir, timestamp + ".json"), json);
        } catch (DisplayableException | IOException ex) {
            JOptionPane.showMessageDialog(pnlComponentTree, ex.getMessage());
        }
    }

    private void takeScreenshot(File f, Component c) throws IOException {
        BufferedImage image
                = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        c.paint(image.getGraphics());
        BufferedImage capturedImage = image;
        ImageIO.write(capturedImage, "png", f);
    }

    private void logComponents(ArrayNode json, Component component, Component root, Set<Component> defaultButtons) {
        if (root == null && component.isVisible()) {
            root = component;
        }

        if (defaultButtons == null) {
            defaultButtons = new HashSet<>();
        }

        ObjectNode jsonItem = json.addObject();
        jsonItem.put("name", component.getClass().getSimpleName());
        Stream.<Class<?>>iterate(component.getClass(), c -> c.getSuperclass() != null, Class::getSuperclass)
                .filter(c -> c.getName().startsWith("javax.swing."))
                .findFirst()
                .ifPresent(c -> jsonItem.put("class", c.getName()));

        if (root != null && component.isVisible()) {
            Point outerLocation = root.getLocationOnScreen();
            Point innerLocation = component.getLocationOnScreen();
            Rectangle innerBounds = component.getBounds();
            jsonItem.put("x", innerLocation.x - outerLocation.x);
            jsonItem.put("y", innerLocation.y - outerLocation.y);
            jsonItem.put("width", innerBounds.width);
            jsonItem.put("height", innerBounds.height);
        }

        if (component instanceof JRootPane) {
            JButton defaultButton = ((JRootPane) component).getDefaultButton();

            if (defaultButton != null) {
                defaultButtons.add(defaultButton);
            }
        } else if (defaultButtons.contains(component)) {
            jsonItem.put("defaultButton", true);
        }

        if (component instanceof Container) {
            Container container = (Container) component;

            for (int i = 0; i < container.getComponentCount(); i++) {
                logComponents(json, container.getComponent(i), root, defaultButtons);
            }
        }
    }

}
