package at.dms.kjc.sir.linear;

/**
 * Represents a pipeline combination transform. Combines two filter that
 * come one after another in a pipeline into a single filter that does
 * the same work. This combination might require each of the individual
 * filters to be expanded by some factor, and then a matrix multiplication
 * can be performed.<p>
 * $Id: LinearTransformPipeline.java,v 1.5 2002-10-23 21:12:44 aalamb Exp $
 **/
class LinearTransformPipeline extends LinearTransform {
    /** The upstream filter representation. **/
    LinearFilterRepresentation upstreamRep;
    /** the downstream filter representation. **/
    LinearFilterRepresentation downstreamRep;
    /** The number of times that we need to expand the upstream rep. **/
    int upstreamExpandFactor;
    /** The number of times that we need to expand the downstream rep. **/
    int downstreamExpandFactor;
    /** the new pop count for the combined filter after the expansion. **/
    int newPopCount;

    /**
     * Creates a new pipeline transformation by expanding the upstream rep
     * by a factor upFactor, expanding the downstream factor by downfactor
     * and then combining the two using the matrix multiply (see transform below).
     **/
    private LinearTransformPipeline(int upFactor,
				    LinearFilterRepresentation up,
				    int downFactor,
				    LinearFilterRepresentation down,
				    int newPop) {
	this.upstreamExpandFactor   = upFactor;
	this.upstreamRep            = up;
	this.downstreamExpandFactor = downFactor;
	this.downstreamRep          = down;
	this.newPopCount            = newPop;
    }


    /**
     * Tries to combine this LinearFilterRepresentation with other.
     * LFRs represent the calculations that a filter performs, by combining two
     * LFRs we hope to represent the calculations that the two filters cascaded one
     * after another form. <p>
     * 
     * Combining only makes sense for two filters with the following properties:
     * <ul>
     * <li> The push rate of upstream one is equal to the peek rate of the downstream one
     * </ul>
     *
     * It is interesting to note that the above suggests that some filters are not combinable
     * which we think is the general case. However, we can also possibly do the
     * equivalent of matrix unrolling on both this LFR and the other LFR to get the above
     * condition to hold.<p>
     *
     * If filter one computes y = xA1 + b1 and filter 2 computes y=xA2 + b2 then
     * the overall filter filter1 --> filter 2 will compute
     * y = (xA1 + b1)A2 + b2 = xA1A2 + (b1A2 + b2), which itself can be represented  
     * with the LFR: A = A1A2 and b = (b1A2 + b2).
     *
     * The LFR that represents both filters cascaded is returned if we can find it, and
     * null is returned if we can not.
     **/
    public LinearFilterRepresentation transform() throws NoTransformPossibleException {
	LinearPrinter.println("Transforming pipeline combination (upfact=" +
			      this.upstreamExpandFactor +
			      ", downfact=" + this.downstreamExpandFactor + ")");

	// expand if need be
	LinearFilterRepresentation upstreamExpandedRep  = this.upstreamRep.expand(this.upstreamExpandFactor);
	LinearFilterRepresentation downstreamExpandedRep = this.downstreamRep.expand(this.downstreamExpandFactor);

	//LinearPrinter.println("Combining pipeline. Upstream A = \n" + upstreamExpandedRep.getA());
	//LinearPrinter.println("Combining pipeline. Downstream A = \n" + downstreamExpandedRep.getA());

	// If the dimensions match up, then perform the actual matrix
	// multiplication
	if (upstreamExpandedRep.getPushCount() == downstreamExpandedRep.getPeekCount()) {
	    FilterMatrix A1 = upstreamExpandedRep.getA();
	    FilterVector b1 = upstreamExpandedRep.getb();
	    FilterMatrix A2 = downstreamExpandedRep.getA();
	    FilterVector b2 = downstreamExpandedRep.getb();
	    
	    // compute the the new A = A1A2
	    FilterMatrix newA = A1.times(A2);
	    // compute the new b = (b1A2 + b2)
	    FilterVector newb = FilterVector.toVector((b1.times(A2)).plus(b2));

	    // return a new LFR with newA and newb 
	    return new LinearFilterRepresentation(newA, newb, newPopCount);
	} else {
	    // we couldn't combine the matricies, complain!
	    throw new RuntimeException("Pipeline is impossible to expand -- should not be a LinearTransformPipeline");
	}
    }

    /**
     * Calculates a LinearTransform defining how to transform a
     * pipeline combination of linear reps (eg one right after the next)
     * into a single linear rep. The filters look like
     * upstream->downstream, as you might imagine.<p>
     *
     * The analysis proceeds the following steps:<p>
     * <ul>
     * <li> if the two filters happen to have upstream.push = downstream.peek, we are
     *      golden, and no expansion is necessary.
     * <li> if the downstream filter has peek=pop, then we can expand
     *      the execution of both filters by a factor to ensure
     *      upstream.push = downstream.peek and we don't end up with
     *      any buffering issues.
     * <li> it is unclear what we are going to do in the general case, as we can't just
     *      get rid of buffering. Instead, we will probably resort to doing
     *      redundant computations.
     * </ul>
     **/
    public static LinearTransform calculate(LinearFilterRepresentation upstream,
					    LinearFilterRepresentation downstream) {
	// if the downstream filter has push equal to their pop, then
	// we can combine them by expanding to make upstream.pop = downstream.peek
	// and not trouble ourselves with any state embodied by the difference
	// between downstream.peek and downstream.pop
	if (downstream.getPeekCount() == downstream.getPopCount()) {
	    int expandLcm = lcm(upstream.getPushCount(), downstream.getPopCount());
	    // a few paranoia checks
	    if (((expandLcm % upstream.getPushCount()) != 0) ||
		((expandLcm % downstream.getPopCount()) != 0)) {
		throw new RuntimeException("Inconsistent expansion factors.");
	    }
	    int upFact   = expandLcm / upstream.getPushCount();
	    int downFact = expandLcm / downstream.getPopCount();
	    return new LinearTransformPipeline(upFact, upstream,
					       downFact, downstream,
					       (upstream.getPopCount() * upFact)); // new pop count
	    // check for two FIRs in a row, and handle appropriately
	} else if (upstream.isFIR() && downstream.isFIR()) {
	    // actually, to combine FIRs, things are easy. Expand the upstream
	    // filter by the number of outputs that the downstream filter peeks at.
	    // then, use the pop count of the downstream filter. (which is going to be 1)
	    return new LinearTransformPipeline(downstream.getPeekCount(), upstream,
					       1, downstream,
					       (downstream.getPopCount()));
	} else {
	    // we don't know how to transform this pipeline combination
	    return new LinearTransformNull("don't know how to combine ([peek,pop,push]):" +
					   "[" + (upstream.getPeekCount() + "," +
						  upstream.getPopCount() + "," +
						  upstream.getPushCount()) + "]-->" +
					   "[" + (downstream.getPeekCount() + "," +
						  downstream.getPopCount() + "," +
						  downstream.getPushCount()) + "]");
	    
	}
    }

}
