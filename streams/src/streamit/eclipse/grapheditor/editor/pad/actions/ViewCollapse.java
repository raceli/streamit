/*
 * Created on Dec 5, 2003
 */
package streamit.eclipse.grapheditor.editor.pad.actions;

import java.awt.event.ActionEvent;

import streamit.eclipse.grapheditor.editor.GPGraphpad;
import streamit.eclipse.grapheditor.editor.utils.Utilities;
import streamit.eclipse.grapheditor.graph.GEContainer;
import streamit.eclipse.grapheditor.graph.GEStreamNode;
import streamit.eclipse.grapheditor.graph.GraphStructure;
import streamit.eclipse.grapheditor.graph.utils.JGraphLayoutManager;

/**
 * Action to collapse a GEStreamNode.
 * @author jcarlos
 *
 */
public class ViewCollapse extends AbstractActionDefault {

    /**
     * Constructor for ViewCollapse.
     * @param graphpad GPGraphpad
     */
    public ViewCollapse(GPGraphpad graphpad) {
        super(graphpad);
    }
    
    /**
     * Collapse a GEStreamNode. Only allowed to collapse a single GEStreamNode at 
     * a time. If the GEStreamNode to be collapsed is a GEContainer, then everything
     * inside the node will be collapsed. If the selected node is a GEContainer,
     * then the encapsulating node (which is a GEContainer) will be the one that is 
     * collapsed.
     */
    public void actionPerformed(ActionEvent e) 
    {
        graphpad.getCurrentDocument().setResizeAction(null);
        GraphStructure graphStruct = graphpad.getCurrentDocument().getGraphStructure();
        int currentLevelView = graphStruct.containerNodes.getCurrentLevelView();
        
        Object[] cells = getCurrentGraph().getSelectionCells();
        getCurrentGraph().clearSelection();
            
        if (graphpad.getCurrentGraph() .getScale() < 8) 
            {
                /** No specific cell was selected - collapse everything at the current level **/
                if (cells.length == 0)
                    {
                        graphStruct.containerNodes.collapseContainersAtLevel(currentLevelView);
                        JGraphLayoutManager manager = new JGraphLayoutManager(graphStruct);
                        manager.arrange();
                        graphStruct.containerNodes.setCurrentLevelView(--currentLevelView);
                
                    }
                /** A single cell was selected to be collapsed **/
                else if (cells.length == 1) 
                    {
                        GEContainer container = null;
                        int firstCell = 0;
                
                        /** Can only collapse GEStreamNodes **/
                        if (!(cells[firstCell] instanceof GEStreamNode))
                            {
                                System.err.println("WARNING: CAN ONLY COLLAPSE GESTREAMNODES");
                                return;
                            }
                
                        /** Selected a container node to collapse **/
                        if (cells[firstCell] instanceof GEContainer)
                            {
                                container =  (GEContainer) cells[firstCell];
                                currentLevelView = container.getDepthLevel();
                    
                                // TODO: make sure we are getting the right max level
                                System.out.println("Current Level View is " + graphStruct.containerNodes.getCurrentLevelView());
                                System.out.println("Node to collapse is in level "+ currentLevelView);
                    
                                //TODO: not sure if this is right -only do this for containers contained by this
                    
                                for (int j = graphStruct.containerNodes.getCurrentLevelView(); j > currentLevelView; j--)
                                    {
                                        graphStruct.containerNodes.collapseContainersAtLevel(j);
                                    }
            
                                container.collapse();
                                JGraphLayoutManager manager = new JGraphLayoutManager(graphStruct);
                                manager.arrange();
                    
                                /** Need to update the current level view so that if a global expand/collapse
                                 * occurs, the node can be updated accordingly */
                                graphStruct.containerNodes.setCurrentLevelView(currentLevelView);
                    
    
                            }
                        /** Selected non-container node to collapse - will collapse encapsulating node **/
                        else
                            {
                                GEStreamNode node = (GEStreamNode) cells[firstCell];
                                container = ((GEContainer) node.getEncapsulatingNode());
                                currentLevelView = container.getDepthLevel();
                    
                                // TODO: make sure we are getting the right level
                                for (int j = graphStruct.containerNodes.getCurrentLevelView(); j > currentLevelView ; j--)
                                    {
                                        graphStruct.containerNodes.collapseContainersAtLevel(j);
                                    }
                                container.collapse();
                                JGraphLayoutManager manager = new JGraphLayoutManager(graphStruct);
                                manager.arrange();
                                graphStruct.containerNodes.setCurrentLevelView(currentLevelView);
                    
                    
                            }               
                    }
                /** More than one cell was selected - this is not allowed **/
                else 
                    {   
                        System.out.println("WARNING: CAN ONLY SELECT ONE CELL TO COLLAPSE AT A TIME");
                        return;
                    }
    
                /** Set the current level to zero in case that it becomes negative**/
                if (currentLevelView < 0 )
                    {
                        graphStruct.containerNodes.setCurrentLevelView(0);
                    }
    
                /** Set the container nodes in their correspoding locations **/
                ViewSetContainerLocation ac = (ViewSetContainerLocation) graphpad.getCurrentActionMap().
                    get(Utilities.getClassNameWithoutPackage(ViewSetContainerLocation.class));
                ac.actionPerformed(null);
    
    
            
                /** Zoom into the newly collapsed graph **/
                graphpad.getCurrentDocument().setScale(graphpad.getCurrentGraph().getScale() * 1.2);
                if (graphpad.getCurrentGraph().getSelectionCell() != null)
                    {
                        graphpad.getCurrentGraph().scrollCellToVisible(graphpad.getCurrentGraph().getSelectionCell());
                    }   
            }
    }
}
