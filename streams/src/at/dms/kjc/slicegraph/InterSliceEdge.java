package at.dms.kjc.slicegraph;

import java.util.HashMap;

import at.dms.kjc.backendSupport.FilterInfo;

/**
 *  An InterSliceEdge represents an edge in the partitioned stream graph between slices.
 *  But it actually connects {@link OutputSliceNode}s to {@link InputSliceNodes}.
 * 
 * @author mgordon
 *
 */
public class InterSliceEdge extends Edge implements at.dms.kjc.DeepCloneable, Comparable<InterSliceEdge>{
    private static HashMap<EdgeDescriptor, InterSliceEdge> edges =
        new HashMap<EdgeDescriptor, InterSliceEdge>();
    
    /**
     * No argument constructor, FOR AUTOMATIC CLONING ONLY.
     */
    private InterSliceEdge() {
        super();
    }
    
    /**
     * Full constructor, (type will be inferred from src / dest).
     * @param src   Source of directed edge as OutputSliceNode
     * @param dest  Destination of directed edga as InputSliceNode
     */
    public InterSliceEdge(OutputSliceNode src, InputSliceNode dest) {
        super(src,dest);
        
        //make sure we did not create this edge before!
        EdgeDescriptor edgeDscr = new EdgeDescriptor(src, dest);      
        InterSliceEdge edge = edges.get(edgeDscr);
        assert (edge == null) : "trying to create 2 identical edges";
        //remember this edge
        edges.put(edgeDscr, this);
    }

    /**
     * Partial constructor: {@link #setDest(InputSliceNode)} later.
     * @param src 
     */
    public InterSliceEdge(OutputSliceNode src) {
        super();
        this.src = src;        
    }

    /**
     * Partial constructor: {@link #setSrc(OutputSliceNode)} later.
     * @param dest
     */
    public InterSliceEdge(InputSliceNode dest) {
        super();
        this.dest = dest;
    }

    public static InterSliceEdge getEdge(OutputSliceNode src, InputSliceNode dest) {
        EdgeDescriptor edgeDscr = new EdgeDescriptor(src, dest);      

        InterSliceEdge edge = edges.get(edgeDscr);

        return edge;
    }
    
    @Override
    public OutputSliceNode getSrc() {
        
        return (OutputSliceNode)src;
    }

    @Override
    public InputSliceNode getDest() {
        return (InputSliceNode)dest;
    }

    @Override
    public void setSrc(SliceNode src) {
        assert src instanceof OutputSliceNode;
        
        //make sure we did not create this edge before!
        EdgeDescriptor edgeDscr = new EdgeDescriptor((OutputSliceNode)src, getDest());      
        InterSliceEdge edge = edges.get(edgeDscr);
        assert (edge == null) : "trying to create 2 identical edges";
        //remember this edge
        edges.put(edgeDscr, this);
        
        super.setSrc(src);
    }

    @Override
    public void setDest(SliceNode dest) {
        assert dest instanceof InputSliceNode;
        //make sure we did not create this edge before!
        EdgeDescriptor edgeDscr = new EdgeDescriptor(getSrc(), (InputSliceNode)dest);      
        InterSliceEdge edge = edges.get(edgeDscr);
        assert (edge == null) : "trying to create 2 identical edges";
        //remember this edge
        edges.put(edgeDscr, this);
        
        super.setDest(dest);
    }
    /**
     * The number of items that traverse this edge in the initialization
     * stage.
     * 
     * @return The number of items that traverse this edge in the initialization
     * stage. 
     */
    public int initItems() {
        int itemsReceived, itemsSent;

        FilterInfo next = FilterInfo.getFilterInfo((FilterSliceNode) ((InputSliceNode)dest)
                                                   .getNext());
        
        itemsSent = (int) ((double) next.initItemsReceived() * ((InputSliceNode)dest).ratio(this, SchedulingPhase.INIT));
        //System.out.println(next.initItemsReceived()  + " * " + ((InputSliceNode)dest).ratio(this));
        
        // calculate the items the output slice sends
        FilterInfo prev = FilterInfo.getFilterInfo((FilterSliceNode) ((OutputSliceNode)src)
                                                   .getPrevious());
        itemsReceived = (int) ((double) prev.initItemsSent() * ((OutputSliceNode)src).ratio(this, SchedulingPhase.INIT));

        if (itemsSent != itemsReceived) {
            System.out.println("*** Init: Items received != Items Sent!");
            System.out.println(prev + " -> " + next);
            System.out.println("Mult: " + prev.getMult(SchedulingPhase.INIT) + " " +  
                    next.getMult(SchedulingPhase.INIT));
            System.out.println("Push: " + prev.prePush + " " + prev.push);
            System.out.println("Pop: " + next.pop);
            System.out.println("Init items Sent * Ratio: " + prev.initItemsSent() + " * " +
                    ((OutputSliceNode)src).ratio(this, SchedulingPhase.INIT));
            System.out.println("Items Received: " + next.initItemsReceived(true));
            System.out.println("Ratio received: " + ((InputSliceNode)dest).ratio(this, SchedulingPhase.INIT));
            
        }
        
        // see if they are different
        assert (itemsSent == itemsReceived) : "Calculating init stage: items received != items send on buffer: "
            + src + " (" + itemsSent + ") -> (" + itemsReceived + ") "+ dest;

        return itemsSent;
    }

    /**
     * @return The amount of items (not counting typesize) that flows 
     * over this edge in the steady state.
     */
    public int steadyItems() {
        int itemsReceived, itemsSent;

        // calculate the items the input slice receives
        FilterInfo next = FilterInfo.getFilterInfo(((InputSliceNode)dest).getNextFilter());
        itemsSent = (int) ((next.steadyMult * next.pop) * ((double) ((InputSliceNode)dest)
                                                           .getWeight(this, SchedulingPhase.STEADY) / ((InputSliceNode)dest).totalWeights(SchedulingPhase.STEADY)));

        // calculate the items the output slice sends
        FilterInfo prev = FilterInfo.getFilterInfo((FilterSliceNode) ((OutputSliceNode)src)
                                                   .getPrevious());
        itemsReceived = (int) ((prev.steadyMult * prev.push) * ((double) ((OutputSliceNode)src)
                                                                .getWeight(this, SchedulingPhase.STEADY) / ((OutputSliceNode)src).totalWeights(SchedulingPhase.STEADY)));

        assert (itemsSent == itemsReceived) : "Calculating steady state: items received != items sent on buffer "
            + itemsSent + " " + itemsReceived + " " + prev + " " + next;

        return itemsSent;
    }

   /**
    * The number of items sent over this link in one call of the link in the prime
    * pump stage, the link might be used many times in the prime pump stage conceptually 
    * using the rotating buffers.
    * 
    * @return ...
    */
    public int primePumpItems() {
        return (int) ((double) FilterInfo.getFilterInfo(((OutputSliceNode)src).getPrevFilter())
                      .totalItemsSent(SchedulingPhase.PRIMEPUMP) * ((OutputSliceNode)src).ratio(this, SchedulingPhase.STEADY));
    }
    
    /**
     * Compare two intersliceedges based on the number of steady items
     * 
     * @param other the other interslice edge
     * @return -1, 0, 1
     */
    public int compareTo(InterSliceEdge other) {
        if (this.steadyItems() < other.steadyItems())
            return -1;
        else if (this.steadyItems() > other.steadyItems())
            return 1;
        return 0;
    }
    private static class EdgeDescriptor {
        public OutputSliceNode src;
        public InputSliceNode dest;

        public EdgeDescriptor(OutputSliceNode src, InputSliceNode dest) {
            this.src = src;
            this.dest = dest;
        }
        
        public EdgeDescriptor(Slice src, Slice dest) {
            this(src.getTail(), dest.getHead());
        }

        public EdgeDescriptor(InterSliceEdge edge) {
            this(edge.getSrc(), edge.getDest());
        }
        
        public boolean equals(Object obj) {
            if(obj instanceof EdgeDescriptor) {
                EdgeDescriptor edge = (EdgeDescriptor)obj;
                
                if(this.src.equals(edge.src) &&
                   this.dest.equals(edge.dest))
                    return true;
                
                return false;
            }
            
            return false;
        }
        
        public int hashCode() {
            return src.hashCode() + dest.hashCode();
        }
    }
}
