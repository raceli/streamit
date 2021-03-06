package at.dms.kjc.slicegraph.fission;

import java.util.*;
import at.dms.kjc.*;
import at.dms.kjc.backendSupport.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.slicegraph.*;

public class PipelineFissioner {

    // Stores mapping from slice to fizzed copies of the slice
    // Fizzed copies of slices are kept in a specific order since order matters
    private static HashMap<Slice, LinkedList<Slice>> sliceToFizzedCopies =
        new HashMap<Slice, LinkedList<Slice>>();

    public static boolean isFizzed(Slice slice) {
        return sliceToFizzedCopies.containsKey(slice);
    }

    public static int getFizzAmount(Slice slice) {
        if(!isFizzed(slice))
            return 1;

        return sliceToFizzedCopies.get(slice).size();
    }

    public static int getFizzIndex(Slice slice) {
        if(!isFizzed(slice))
            return 0;

        return sliceToFizzedCopies.get(slice).indexOf(slice);
    }

    private static FilterSliceNode getFirstFilter(Slice slice) {
        return slice.getFirstFilter();
    }

    private static FilterSliceNode getLastFilter(Slice slice) {
        assert (slice.getTail().getPrevious() instanceof FilterSliceNode) :
        "Can't get last FilterSliceNode from Slice";

        return (FilterSliceNode)slice.getTail().getPrevious();
    }

    private static int[] toArraySingleInt(LinkedList<Integer> list) {
        int[] array = new int[list.size()];

        for(int x = 0 ; x < array.length ; x++)
            array[x] = list.get(x).intValue();

        return array;
    }

    private static InterSliceEdge[] toArraySingle(LinkedList<InterSliceEdge> list) {
        InterSliceEdge[] array = new InterSliceEdge[list.size()];

        for(int x = 0 ; x < array.length ; x++)
            array[x] = list.get(x);

        return array;
    }

    private static InterSliceEdge[][] toArrayDouble(LinkedList<LinkedList<InterSliceEdge>> list) {
        InterSliceEdge[][] array = new InterSliceEdge[list.size()][];

        LinkedList<InterSliceEdge> tempList;
        for(int x = 0 ; x < array.length ; x++) {
            tempList = list.get(x);

            array[x] = new InterSliceEdge[tempList.size()];
            for(int y = 0 ; y < array[x].length ; y++) {
                array[x][y] = tempList.get(y);
            }
        }

        return array;
    }

    private static InterSliceEdge getEdge(Slice src, Slice dest) {
        return FissionEdgeMemoizer.getEdge(src, dest);
    }
    
    public static boolean canFizz(Slice slice, int fizzAmount, boolean debug) {
        // Get information on Slice rates
        FilterInfo.reset();

        FilterSliceNode filter = getFirstFilter(slice);
        FilterInfo filterInfo = FilterInfo.getFilterInfo(filter);
        
        int slicePeek = filterInfo.peek;
        int slicePop = filterInfo.pop;
        int slicePush = filterInfo.push;

        int slicePrePeek = filterInfo.prePeek;
        int slicePrePop = filterInfo.prePop;
        int slicePrePush = filterInfo.prePush;

        int sliceInitMult = filterInfo.initMult;
        int sliceSteadyMult = filterInfo.steadyMult;
        int sliceCopyDown = filterInfo.copyDown;

        // Get Slice sources and dests
        Slice sources[] = slice.getHead().getSourceSlices(SchedulingPhase.STEADY).toArray(new Slice[0]);
        Slice dests[] = slice.getTail().getDestSlices(SchedulingPhase.STEADY).toArray(new Slice[0]);

        // If sources are fizzed
        if(isFizzed(sources[0])) {
            // Make sure sources belong to the same set of fizzed Slices
            LinkedList <Slice> fizzedCopies1 = sliceToFizzedCopies.get(sources[0]);
            
            // Make sure that sources are fizzed by fizzAmount
            if(fizzedCopies1.size() != fizzAmount) {
                if(debug) System.out.println("Can't fizz: Sources fizzed by a different amount");
                return false;
            }
        }
        
        // If dests are fizzed
        if(isFizzed(dests[0])) {
   
            // Make sure that dests belong to the same set of fizzed Slices
            LinkedList <Slice> fizzedCopies1 = sliceToFizzedCopies.get(dests[0]);
 
            // Make sure that dests are fizzed by fizzAmount
            if(fizzedCopies1.size() != fizzAmount) {
                if(debug) System.out.println("Can't fizz: Dests fizzed by different amount");
                return false;
            }
        }
        
        return canFizz(slice, debug);
    }

    public static boolean canFizz(Slice slice, boolean debug) {

        // Get information on Slice rates
        FilterInfo.reset();

        FilterSliceNode filter = getFirstFilter(slice);
        FilterInfo filterInfo = FilterInfo.getFilterInfo(filter);
        
        int slicePeek = filterInfo.peek;
        int slicePop = filterInfo.pop;
        int slicePush = filterInfo.push;

        int slicePrePeek = filterInfo.prePeek;
        int slicePrePop = filterInfo.prePop;
        int slicePrePush = filterInfo.prePush;

        int sliceInitMult = filterInfo.initMult;
        int sliceSteadyMult = filterInfo.steadyMult;
        int sliceCopyDown = filterInfo.copyDown;

        // Get Slice sources and dests
        Slice sources[] = slice.getHead().getSourceSlices(SchedulingPhase.STEADY).toArray(new Slice[0]);
        Slice dests[] = slice.getTail().getDestSlices(SchedulingPhase.STEADY).toArray(new Slice[0]);

        // Check to see if Slice is a source/sink.  Don't fizz source/sink.
        if(sources.length == 0 || dests.length == 0) {
            if(debug) System.out.println("Can't fizz: Slice is source or sink");
            return false;
        }

        // Check to see if Slice has file reader/writer.  Don't fizz file
        // reader/writer
        if(slice.getTail().isFileInput() || slice.getHead().isFileOutput()) {
            if(debug) System.out.println("Can't fizz: Slice contains file reader/writer");
            return false;
        }

        // Make sure that Slice has only one FilterSliceNode
        if(!(slice.getNumFilters() == 1)) {
            if(debug) System.out.println("Can't fizz: Slice has more than one FilterSliceNode");
            return false;
        }

        // Check to make sure that Slice is stateless
        if(MutableStateExtractor.hasMutableState(slice.getFirstFilter().getFilter())) {
            if(debug) System.out.println("Can't fizz: Slice is not stateless!!");
            return false;
        }

        // Check to see if FilterSliceNode contains a linear filter.  At the
        // moment, we can't fizz linear filters
        if(filter.getFilter().isLinear()) {
            if(debug) System.out.println("Can't fizz: Slice contains linear filter, presently unsupported");
            return false;
        }

   
        
        // Make sure that sources only push to this Slice
        for(int x = 0 ; x < sources.length ; x++) {
            if(sources[x].getTail().getDestSlices(SchedulingPhase.STEADY).size() > 1) {
                if(debug) System.out.println("Can't fizz: Sources for Slice send to other Slices");
                return false;
            }
        }

        // Make sure that dests only pop from this Slice
        for(int x = 0 ; x < dests.length ; x++) {
            if(dests[x].getHead().getSourceSlices(SchedulingPhase.STEADY).size() > 1) {
                if(debug) System.out.println("Can't fizz: Dests for Slice receives from other Slices");
                return false;
            }
        }

        // Make sure that sources are not a mix of fizzed and unfizzed Slices
        for(int x = 0 ; x < sources.length - 1 ; x++) {
            assert isFizzed(sources[x]) == isFizzed(sources[x + 1]) :
            "Slice sources are a mix of fizzed and unfizzed Slices";
        }

        // Make sure that dests are not a mix of fizzed and unfizzed Slice
        for(int x = 0 ; x < dests.length - 1 ; x++) {
            assert isFizzed(dests[x]) == isFizzed(dests[x + 1]) :
            "Slice dests are a mix of fizzed and unfizzed Slices";
        }

        // If sources are fizzed
        if(isFizzed(sources[0])) {

            // Make sure sources belong to the same set of fizzed Slices
            LinkedList <Slice> fizzedCopies1 = sliceToFizzedCopies.get(sources[0]);
            LinkedList <Slice> fizzedCopies2;

            for(int x = 1 ; x < sources.length ; x++) {
                fizzedCopies2 = sliceToFizzedCopies.get(sources[x]);

                assert fizzedCopies1.equals(fizzedCopies2) :
                "Slice sources do not belong to the same set of fizzed slices";
            }

        }

        // If dests are fizzed
        if(isFizzed(dests[0])) {
   
            // Make sure that dests belong to the same set of fizzed Slices
            LinkedList <Slice> fizzedCopies1 = sliceToFizzedCopies.get(dests[0]);
            LinkedList <Slice> fizzedCopies2;

            for(int x = 1 ; x < dests.length ; x++) {
                fizzedCopies2 = sliceToFizzedCopies.get(dests[x]);

                assert fizzedCopies1.equals(fizzedCopies2) :
                "Slice dests do not belong to the same set of fizzed slices";
            }
        }
        
        // Make sure that rates match between Slice and its sources/dests
        FilterInfo sourceInfo = FilterInfo.getFilterInfo(getLastFilter(sources[0]));
        FilterInfo destInfo = FilterInfo.getFilterInfo(getFirstFilter(dests[0]));

        assert(sources.length * sourceInfo.steadyMult * sourceInfo.push ==
               sliceSteadyMult * slicePop) :
        "Rates between sources and Slice do not match";

        assert(sliceSteadyMult * slicePush ==
               dests.length * destInfo.steadyMult * destInfo.pop) :
        "Rates between Slice and dests do not match";

        return true;
    }

    /**
     *
     * Attempts to fizz a Slice by fizzAmount.  Returns false if fizzing is not
     * possible.
     *
     * This function makes numerous assumptions:
     *
     * 1) The Slice to be fizzed either takes input from a single Slice, or a 
     *    set of Slices previously created by fizzing a Slice by a factor of
     *    fizzAmount
     *
     * 2) The Slice to be fizzed either outputs to a single Slice, or a set of
     *    Slices previously created by fizzing a Slice by a factor of fizzAmount
     *
     * 3) Input Slices push only to the Slice being fizzed
     *
     * 4) Output Slices pop only from the Slice being fizzed
     *
     * 5) Slice has only one FilterSliceNode
     *
     * 6) Multiplicity of FilterSliceNode is equally divisible by fizzAmount
     *
     * 7) Rates match between Slice and its sources/dests
     *
     * 8) CopyDown < SteadyMult * Pop
     *
     * These assumptions are checked by initially calling canFizz().
     * If these assumptions are not met, the function returns false.  Otherwise,
     * the function proceeds to fizz the given filter, while assuming that the
     * above assumptions are met.
     */
    public static boolean fizzSlice(Slice slice, int fizzAmount) {

        if(!canFizz(slice, fizzAmount, false))
            return false;

        Slice sliceClones[] = new Slice[fizzAmount];

        LinkedList<InterSliceEdge> edgeSet;
        LinkedList<LinkedList<InterSliceEdge>> edgeSetSet;
        LinkedList<Integer> weights;

        // Get information on Slice rates
        FilterInfo.reset();

        FilterSliceNode filter = getFirstFilter(slice);
        FilterInfo filterInfo = FilterInfo.getFilterInfo(filter);

        int slicePeek = filterInfo.peek;
        int slicePop = filterInfo.pop;
        int slicePush = filterInfo.push;

        int slicePrePeek = filterInfo.prePeek;
        int slicePrePop = filterInfo.prePop;
        int slicePrePush = filterInfo.prePush;

        int sliceInitMult = filterInfo.initMult;
        int sliceSteadyMult = filterInfo.steadyMult;
        int sliceCopyDown = filterInfo.copyDown;

        // Make sure that multiplicity of single FilterSliceNode is divisible
        // by fizzAmount
        assert sliceSteadyMult % fizzAmount == 0 : "Multiplicity not divisible by fission amount";
            
        // Check copyDown constraint: copyDown < mult * pop
        assert sliceCopyDown < sliceSteadyMult * slicePop : "Can't fizz: Slice does not meet copyDown constraint";
                    
        // TODO: Remove debug println
        //System.out.println("Slice copy down: " + sliceCopyDown);   

        // Get Slice sources and destinations, ordered by how they were
        // originally fizzed
        Slice sources[] = slice.getHead().getSourceSlices(SchedulingPhase.STEADY).toArray(new Slice[0]);
        Slice dests[] = slice.getTail().getDestSlices(SchedulingPhase.STEADY).toArray(new Slice[0]);

        if(sources.length > 1)
            sources = sliceToFizzedCopies.get(sources[0]).toArray(new Slice[0]);

        if(dests.length > 1)
            dests = sliceToFizzedCopies.get(dests[0]).toArray(new Slice[0]);

        // Clear edges memoized in previous calls to fizzSlice.  This is done
        // because we don't care about most of the previously memoized edges.
        // Edges that should be memoized will be (re)added to the memoizer in
        // the next step.
        FissionEdgeMemoizer.reset();

        // Add edges surrounding the Slice to the edge memoizer, so that they 
        // can be reused later in fission
        Set<InterSliceEdge> origEdges;
        
        origEdges = slice.getHead().getSourceSet(SchedulingPhase.INIT);
        for(InterSliceEdge edge : origEdges)
            FissionEdgeMemoizer.addEdge(edge);

        origEdges = slice.getHead().getSourceSet(SchedulingPhase.STEADY);
        for(InterSliceEdge edge : origEdges)
            FissionEdgeMemoizer.addEdge(edge);

        origEdges = slice.getTail().getDestSet(SchedulingPhase.INIT);
        for(InterSliceEdge edge : origEdges)
            FissionEdgeMemoizer.addEdge(edge);

        origEdges = slice.getTail().getDestSet(SchedulingPhase.STEADY);
        for(InterSliceEdge edge : origEdges)
            FissionEdgeMemoizer.addEdge(edge);

        // Fill array with clones of Slice, put original copy first in array
        sliceClones[0] = slice;
        for(int x = 1 ; x < fizzAmount ; x++)
            sliceClones[x] = (Slice)ObjectDeepCloner.deepCopy(slice);

        // Give each Slice clone a unique name
        String origName = sliceClones[0].getFirstFilter().getFilter().getName();
        for(int x = 0 ; x < fizzAmount ; x++)
            sliceClones[x].getFirstFilter().getFilter().setName(origName + "_fizz" + x);

        /**********************************************************************
         *                   Setup initialization schedule                    *
         **********************************************************************/

        /*
         * The unfizzed Slice has both an initialization phase and a steady-
         * state phase.  Once the Slice is fizzed, only one of the Slice clones
         * needs to execute the initialization phase.
         *
         * We have chosen that the first Slice clone be the clone to handle
         * initialization.  The remaining Slice clones are simply disabled
         * during initialization.
         */

        // For the first Slice clone, move initialization work into prework.
        // This involves copying the work body into the prework after the
        // original prework body.  The initialization multiplicity is rolled
        // into a loop around the copied work body.

        if(sliceInitMult > 0) {
            JBlock firstWorkBody =
                sliceClones[0].getFirstFilter().getFilter().getWork().getBody();
	    
            JBlock newPreworkBody = new JBlock();
	    
            if(sliceClones[0].getFirstFilter().getFilter().getPrework() != null &&
               sliceClones[0].getFirstFilter().getFilter().getPrework().length > 0 &&
               sliceClones[0].getFirstFilter().getFilter().getPrework()[0] != null &&
               sliceClones[0].getFirstFilter().getFilter().getPrework()[0].getBody() != null) {
                newPreworkBody.addStatement(sliceClones[0].getFirstFilter().getFilter().getPrework()[0].getBody());
            }

            JVariableDefinition initMultLoopVar =
                new JVariableDefinition(0,
                                        CStdType.Integer,
                                        "initMultCount",
                                        new JIntLiteral(0));

            JVariableDeclarationStatement initMultLoopVarDecl = new JVariableDeclarationStatement(initMultLoopVar);
            newPreworkBody.addStatementFirst(initMultLoopVarDecl);
	    
            JRelationalExpression initMultLoopCond =
                new JRelationalExpression(JRelationalExpression.OPE_LT,
                                          new JLocalVariableExpression(initMultLoopVar),
                                          new JIntLiteral(sliceInitMult));
	    
            JExpressionStatement initMultLoopIncr =
                new JExpressionStatement(new JAssignmentExpression(new JLocalVariableExpression(initMultLoopVar),
                                                                   new JAddExpression(new JLocalVariableExpression(initMultLoopVar),
                                                                                      new JIntLiteral(1))));
	    
            JForStatement initMultLoop =
                new JForStatement(new JEmptyStatement(),
                                  initMultLoopCond,
                                  initMultLoopIncr,
                                  (JBlock)ObjectDeepCloner.deepCopy(firstWorkBody));
            newPreworkBody.addStatement(initMultLoop);
	    
            if(sliceClones[0].getFirstFilter().getFilter().getPrework() == null ||
               sliceClones[0].getFirstFilter().getFilter().getPrework().length == 0 ||
               sliceClones[0].getFirstFilter().getFilter().getPrework()[0] == null) {
                JMethodDeclaration newPreworkMethod =
                    new JMethodDeclaration(null,
                                           at.dms.kjc.Constants.ACC_PUBLIC,
                                           CStdType.Void,
                                           "fissionPrework",
                                           JFormalParameter.EMPTY,
                                           CClassType.EMPTY,
                                           newPreworkBody,
                                           null,
                                           null);

                sliceClones[0].getFirstFilter().getFilter().setPrework(newPreworkMethod);

                slicePrePeek = 0;
                slicePrePush = 0;
                slicePrePop = 0;
            }
            else {
                sliceClones[0].getFirstFilter().getFilter().getPrework()[0].setBody(newPreworkBody);
            }
	    
            // For the first Slice clone, adjust prework rates to reflect that 
            // initialization work was moved into prework
	    
            slicePrePeek = Math.max(slicePrePeek,
                                    slicePrePop + (sliceInitMult * slicePop) + (slicePeek - slicePop));
            slicePrePop = slicePrePop + sliceInitMult * slicePop;
            slicePrePush = slicePrePush + sliceInitMult * slicePush;
	    
            sliceClones[0].getFirstFilter().getFilter().getPrework()[0].setPeek(slicePrePeek);
            sliceClones[0].getFirstFilter().getFilter().getPrework()[0].setPop(slicePrePop);
            sliceClones[0].getFirstFilter().getFilter().getPrework()[0].setPush(slicePrePush);
	    
            // Since the initialization work has been moved into prework, set
            // the initialization multiplicity of the first Slice clone to 0
	    
            sliceClones[0].getFirstFilter().getFilter().setInitMult(1);
        }

        // Disable all other Slice clones in initialization.  This involves
        // disabling prework and seting initialization multiplicty to 0

        for(int x = 1 ; x < fizzAmount ; x++) {
            sliceClones[x].getFirstFilter().getFilter().setPrework(null);
            sliceClones[x].getFirstFilter().getFilter().setInitMult(0);
        }

        sliceInitMult = 0;

        // Since only the first Slice clone executes, it will be the only Slice
        // clone to receive during initialization.
        //
        // If there are multiple source Slices, it is assumed that only the 
        // first source Slice will execute during initialization.  Only the 
        // first source Slice will transmit during initialization.
        //
        // Setup the splitter-joiner schedules to reflect that only the first
        // source Slice transmits and that only the first Slice clone receives.
 
        edgeSetSet = new LinkedList<LinkedList<InterSliceEdge>>();
        weights = new LinkedList<Integer>();
	
        edgeSet = new LinkedList<InterSliceEdge>();
        edgeSet.add(getEdge(sources[0], sliceClones[0]));
        edgeSetSet.add(edgeSet);
        weights.add(new Integer(1));
	
        sources[0].getTail().setInitWeights(toArraySingleInt(weights));
        sources[0].getTail().setInitDests(toArrayDouble(edgeSetSet));

        sliceClones[0].getHead().setInitWeights(toArraySingleInt(weights));
        sliceClones[0].getHead().setInitSources(toArraySingle(edgeSet));

        for(int x = 1 ; x < sources.length ; x++) {
            sources[x].getTail().setInitWeights(null);
            sources[x].getTail().setInitDests(null);                                             
        }
	
        for(int x = 1 ; x < fizzAmount ; x++) {
            sliceClones[x].getHead().setInitWeights(null);
            sliceClones[x].getHead().setInitSources(null);
        }

        // Since only the first Slice clone executes, it will be the only Slice
        // clone to transmit during initialization.
        //
        // If there are multiple dest Slices, it is assumed that only the first 
        // dest Slice will execute during initialization.  Only the first dest 
        // Slice will receive during initialization.
        //
        // Setup the splitter-joiner schedules to reflect that only the first
        // Slice clone transmits and that only the first dest Slice receives.

        edgeSetSet = new LinkedList<LinkedList<InterSliceEdge>>();
        weights = new LinkedList<Integer>();
	
        edgeSet = new LinkedList<InterSliceEdge>();
        edgeSet.add(getEdge(sliceClones[0], dests[0]));
        edgeSetSet.add(edgeSet);
        weights.add(new Integer(1));
	
        sliceClones[0].getTail().setInitWeights(toArraySingleInt(weights));
        sliceClones[0].getTail().setInitDests(toArrayDouble(edgeSetSet));

        dests[0].getHead().setInitWeights(toArraySingleInt(weights));
        dests[0].getHead().setInitSources(toArraySingle(edgeSet));

        for(int x = 1 ; x < fizzAmount ; x++) {
            sliceClones[x].getTail().setInitWeights(null);
            sliceClones[x].getTail().setDests(null);
        }
	
        for(int x = 1 ; x < dests.length ; x++) {
            dests[x].getHead().setInitWeights(null);
            dests[x].getHead().setInitSources(null);
        }

        // Set prepop for the last Slice clone.  Initially in steady-state, last
        // Slice clone will receive elements that it won't need.  Use prepop to
        // remove these unneeded elements.

        if(Math.max(0, (slicePeek - slicePop) - sliceCopyDown) > 0) {
            if(!sliceClones[fizzAmount - 1].getFirstFilter().getFilter().isTwoStage()) {
                JMethodDeclaration prework = 
                    new JMethodDeclaration(null, at.dms.kjc.Constants.ACC_PUBLIC,
                                           CStdType.Void, "emptyPrework",
                                           JFormalParameter.EMPTY, CClassType.EMPTY,
                                           new JBlock(), null, null);
                
                sliceClones[fizzAmount - 1].getFirstFilter().getFilter().setPrework(prework);
            }
            
            sliceClones[fizzAmount - 1].getFirstFilter().getFilter().getPrework()[0]
                .setPop(Math.max(0, (slicePeek - slicePop) - sliceCopyDown));
        }

        /**********************************************************************
         *                     Setup steady-state schedule                    *
         **********************************************************************/

        // Calculate new steady-state multiplicity based upon fizzAmount.  
        // Because work is equally shared among all Slice clones, steady-state 
        // multiplicity is divided by fizzAmount for each Slice clone

        sliceSteadyMult /= fizzAmount;

        for(int x = 0 ; x < fizzAmount ; x++)
            sliceClones[x].getFirstFilter().getFilter().setSteadyMult(sliceSteadyMult);

        // Construct splitter-joiner schedule between source Slices and Slice 
        // clones

        if(sources.length == 1) {
            /* Only one source Slice, source Slice was not fizzed */

            // Calculate single phase in splitter schedule
            int numDup = slicePeek - slicePop;
            int numSingle = (sliceSteadyMult * slicePop) - (slicePeek - slicePop);

            // Generate steady-state splitter schedule for source Slice
            edgeSetSet = new LinkedList<LinkedList<InterSliceEdge>>();
            weights = new LinkedList<Integer>();

            for(int x = 0 ; x < fizzAmount ; x++) {
                if(numDup > 0) {
                    edgeSet = new LinkedList<InterSliceEdge>();
                    edgeSet.add(getEdge(sources[0], sliceClones[(x + fizzAmount - 1) % fizzAmount]));
                    edgeSet.add(getEdge(sources[0], sliceClones[x]));
                    edgeSetSet.add(edgeSet);
                    weights.add(new Integer(numDup));
                }

                if(numSingle > 0) {
                    edgeSet = new LinkedList<InterSliceEdge>();
                    edgeSet.add(getEdge(sources[0], sliceClones[x]));
                    edgeSetSet.add(edgeSet);
                    weights.add(new Integer(numSingle));
                }
            }

            int rotateAmount = sliceCopyDown;

            while(rotateAmount > 0) {
                if(weights.getFirst().intValue() <= rotateAmount) {
                    rotateAmount -= weights.getFirst().intValue();
		    
                    weights.addLast(weights.removeFirst());
                    edgeSetSet.addLast(edgeSetSet.removeFirst());
                }
                else {
                    weights.addFirst(new Integer(weights.removeFirst().intValue() -
                                                 rotateAmount));

                    edgeSet = new LinkedList<InterSliceEdge>();
                    for(InterSliceEdge edge : edgeSetSet.getFirst())
                        edgeSet.add(edge);

                    weights.add(new Integer(rotateAmount));
                    edgeSetSet.add(edgeSet);

                    rotateAmount = 0;
                }
            }

            sources[0].getTail().setWeights(toArraySingleInt(weights));
            sources[0].getTail().setDests(toArrayDouble(edgeSetSet));

            // Generate steady-state joiner schedules for Slices clones
            for(int x = 0 ; x < fizzAmount ; x++) {
                edgeSet = new LinkedList<InterSliceEdge>();
                weights = new LinkedList<Integer>();

                edgeSet.add(getEdge(sources[0], sliceClones[x]));
                weights.add(new Integer(1));

                sliceClones[x].getHead().setWeights(toArraySingleInt(weights));
                sliceClones[x].getHead().setSources(toArraySingle(edgeSet));
            }
        }
        else if(sources.length == fizzAmount) {
            /* 
             * Multiple source Slices, assume they come from a fizzed Slice
             *
             * NOTE: Both sources and sliceClones have a length of fizzAmount
             *       This fact is used extensively in the following code
             */

            // Get information on source Slices
            FilterSliceNode sourceLastFilter = getLastFilter(sources[0]);
            FilterInfo sourceLastFilterInfo = FilterInfo.getFilterInfo(sourceLastFilter);

            int sourcePush = sourceLastFilterInfo.push;
            int sourcePushMult = sourceLastFilterInfo.steadyMult;

            // Calculate single phase in splitter schedule
            int sourcePushRemaining = sourcePushMult * sourcePush;

            int numDup1 = 0;
            int numSingle1 = 0;
            int numDup2 = 0;
            int numSingle2 = 0;

            if(sliceCopyDown <= slicePeek - slicePop) {
                numDup1 = Math.min(sourcePushRemaining, (slicePeek - slicePop) - sliceCopyDown);
                sourcePushRemaining -= numDup1;

                numSingle1 = Math.min(sourcePushRemaining, (sliceSteadyMult * slicePop) - (slicePeek - slicePop));
                sourcePushRemaining -= numSingle1;

                numDup2 = Math.min(sourcePushRemaining, slicePeek - slicePop);
                sourcePushRemaining -= numDup2;

                numSingle2 = sourcePushRemaining;
            }
            else if(sliceCopyDown <= sliceSteadyMult * slicePop) {
                numDup1 = 0;

                numSingle1 = Math.min(sourcePushRemaining, (sliceSteadyMult * slicePop) - sliceCopyDown);
                sourcePushRemaining -= numSingle1;
		
                numDup2 = Math.min(sourcePushRemaining, (slicePeek - slicePop));
                sourcePushRemaining -= numDup2;
		
                numSingle2 = sourcePushRemaining;
            }
            else {
                assert false : "CopyDown constraint violated";
            }

            // Generate steady-state splitter schedules for source Slices
            // TODO: Remove debug printlns
            //System.out.println("Generating steady-state splitter schedule for multiple source Slices");

            for(int x = 0 ; x < fizzAmount ; x++) {
                //System.out.println("Source slice #" + x);

                edgeSetSet = new LinkedList<LinkedList<InterSliceEdge>>();
                weights = new LinkedList<Integer>();

                if(numDup1 > 0) {
                    /*System.out.println("  EdgeSet");
                    System.out.println("    Edge: " + x + " -> " + (x + fizzAmount - 1) % fizzAmount);
                    System.out.println("    Edge: " + x + " -> " + x);
                    System.out.println("    Weight: " + numDup1);*/
                    edgeSet = new LinkedList<InterSliceEdge>();
                    edgeSet.add(getEdge(sources[x], sliceClones[(x + fizzAmount - 1) % fizzAmount]));
                    edgeSet.add(getEdge(sources[x], sliceClones[x]));
                    edgeSetSet.add(edgeSet);
                    weights.add(new Integer(numDup1));
                }

                if(numSingle1 > 0) {
                    /*System.out.println("  EdgeSet");
                    System.out.println("    Edge: " + x + " -> " + x);
                    System.out.println("    Weight: " + numSingle1);*/
                    edgeSet = new LinkedList<InterSliceEdge>();
                    edgeSet.add(getEdge(sources[x], sliceClones[x]));
                    edgeSetSet.add(edgeSet);
                    weights.add(new Integer(numSingle1));
                }

                if(numDup2 > 0) {
                    /*System.out.println("  EdgeSet");
                    System.out.println("    Edge: " + x + " -> " + x);
                    System.out.println("    Edge: " + x + " -> " + (x + 1) % fizzAmount);
                    System.out.println("    Weight: " + numDup2);*/
                    edgeSet = new LinkedList<InterSliceEdge>();
                    edgeSet.add(getEdge(sources[x], sliceClones[x]));
                    edgeSet.add(getEdge(sources[x], sliceClones[(x + 1) % fizzAmount]));
                    edgeSetSet.add(edgeSet);
                    weights.add(new Integer(numDup2));
                }

                if(numSingle2 > 0) {
                    /*System.out.println("  EdgeSet");
                    System.out.println("    Edge: " + x + " -> " + (x + 1) % fizzAmount);
                    System.out.println("    Weight: " + numSingle2);*/
                    edgeSet = new LinkedList<InterSliceEdge>();
                    edgeSet.add(getEdge(sources[x], sliceClones[(x + 1) % fizzAmount]));
                    edgeSetSet.add(edgeSet);
                    weights.add(new Integer(numSingle2));
                }
		
                sources[x].getTail().setWeights(toArraySingleInt(weights));
                sources[x].getTail().setDests(toArrayDouble(edgeSetSet));
            }
    
            // Generate steady-state joiner schedules for Slice clones
            edgeSet = new LinkedList<InterSliceEdge>();
            weights = new LinkedList<Integer>();

            if((sliceSteadyMult * slicePop) + (slicePeek - slicePop) - sliceCopyDown > 0) {
                edgeSet.add(getEdge(sources[0], sliceClones[0]));
                weights.add(new Integer((sliceSteadyMult * slicePop) + (slicePeek - slicePop) - sliceCopyDown));
            }

            //System.out.println("Slice copy down 2: " + sliceCopyDown);

            if(sliceCopyDown > 0) {
                edgeSet.add(getEdge(sources[fizzAmount - 1], sliceClones[0]));
                weights.add(new Integer(sliceCopyDown));
            }

            sliceClones[0].getHead().setWeights(toArraySingleInt(weights));
            sliceClones[0].getHead().setSources(toArraySingle(edgeSet));

            for(int x = 1 ; x < fizzAmount ; x++) {
                edgeSet = new LinkedList<InterSliceEdge>();
                weights = new LinkedList<Integer>();

                if(sliceCopyDown > 0) {
                    edgeSet.add(getEdge(sources[(x + fizzAmount - 1) % fizzAmount], sliceClones[x]));
                    weights.add(new Integer(sliceCopyDown));
                }
		
                if((sliceSteadyMult * slicePop) + (slicePeek - slicePop) - sliceCopyDown > 0) {
                    edgeSet.add(getEdge(sources[x], sliceClones[x]));
                    weights.add(new Integer((sliceSteadyMult * slicePop) + (slicePeek - slicePop) - sliceCopyDown));
                }

                sliceClones[x].getHead().setWeights(toArraySingleInt(weights));
                sliceClones[x].getHead().setSources(toArraySingle(edgeSet));
            }
        }
        else {
            /*
             * Multiple source Slices, assume they all come from a fizzed Slice
             * Unfortunately, there aren't fizzAmount source Slices, which is a
             *     case we can't presently handle
             *
             * NOTE: Shouldn't actually get here, canFizz() should have caught
             *     this already
             */

            System.out.println("Can't fizz Slice because upstream Slice was " +
                               "not fizzed by the same amount");
            return false;
        }

        // Construct splitter-joiner schedule between Slice clones and dest 
        // Slices

        if(dests.length == 1) {
            /* Only one destination Slice, so destination Slice was not fizzed */

            // Generate steady-state splitter schedules for Slice clones
            for(int x = 0 ; x < fizzAmount ; x++) {
                edgeSetSet = new LinkedList<LinkedList<InterSliceEdge>>();
                weights = new LinkedList<Integer>();

                edgeSet = new LinkedList<InterSliceEdge>();
                edgeSet.add(getEdge(sliceClones[x], dests[0]));
                edgeSetSet.add(edgeSet);
                weights.add(new Integer(1));
		
                sliceClones[x].getTail().setWeights(toArraySingleInt(weights));
                sliceClones[x].getTail().setDests(toArrayDouble(edgeSetSet));
            }

            // Generate steady-state joiner schedule for destination Slice
            edgeSet = new LinkedList<InterSliceEdge>();
            weights = new LinkedList<Integer>();

            for(int x = 0 ; x < fizzAmount ; x++) {
                edgeSet.add(getEdge(sliceClones[x], dests[0]));
                weights.add(new Integer(sliceSteadyMult * slicePush));
            }

            dests[0].getHead().setWeights(toArraySingleInt(weights));
            dests[0].getHead().setSources(toArraySingle(edgeSet));
        }
        else if(dests.length == fizzAmount) {
            /*
             * Multiple destination Slices, assume they come from a fizzed Slice
             *
             * NOTE: Both dests and sliceClones have a length of fizzAmount
             *       This fact is used extensively in the following code
             */

            // Get information on destination Slices
            FilterSliceNode destFirstFilter = getFirstFilter(dests[0]);
            FilterInfo destFirstFilterInfo = FilterInfo.getFilterInfo(destFirstFilter);

            int destPop = destFirstFilterInfo.pop;
            int destPeek = destFirstFilterInfo.peek;
            int destPopMult = destFirstFilterInfo.steadyMult;
            int destCopyDown = destFirstFilterInfo.copyDown;

            // Calculate single phase in splitter schedule
            int slicePushRemaining = sliceSteadyMult * slicePush;

            int numDup1 = 0;
            int numSingle1 = 0;
            int numDup2 = 0;
            int numSingle2 = 0;

            if(destCopyDown <= destPeek - destPop) {
                numDup1 = Math.min(slicePushRemaining, (destPeek - destPop) - destCopyDown);
                slicePushRemaining -= numDup1;

                numSingle1 = Math.min(slicePushRemaining, (destPopMult * destPop) - (destPeek - destPop));
                slicePushRemaining -= numSingle1;

                numDup2 = Math.min(slicePushRemaining, destPeek - destPop);
                slicePushRemaining -= numDup2;

                numSingle2 = slicePushRemaining;
            }
            else if(destCopyDown <= destPopMult * destPop) {
                numDup1 = 0;

                numSingle1 = Math.min(slicePushRemaining, (destPopMult * destPop) - destCopyDown);
                slicePushRemaining -= numSingle1;
		
                numDup2 = Math.min(slicePushRemaining, (destPeek - destPop));
                slicePushRemaining -= numDup2;
		
                numSingle2 = slicePushRemaining;
            }
            else {
                assert false : "CopyDown constraint violated";
            }

            // Generate steady-state splitter schedules for Slices clones
            for(int x = 0 ; x < fizzAmount ; x++) {
                edgeSetSet = new LinkedList<LinkedList<InterSliceEdge>>();
                weights = new LinkedList<Integer>();

                if(numDup1 > 0) {
                    edgeSet = new LinkedList<InterSliceEdge>();
                    edgeSet.add(getEdge(sliceClones[x], dests[(x + fizzAmount - 1) % fizzAmount]));
                    edgeSet.add(getEdge(sliceClones[x], dests[x]));
                    edgeSetSet.add(edgeSet);
                    weights.add(new Integer(numDup1));
                }

                if(numSingle1 > 0) {
                    edgeSet = new LinkedList<InterSliceEdge>();
                    edgeSet.add(getEdge(sliceClones[x], dests[x]));
                    edgeSetSet.add(edgeSet);
                    weights.add(new Integer(numSingle1));
                }

                if(numDup2 > 0) {
                    edgeSet = new LinkedList<InterSliceEdge>();
                    edgeSet.add(getEdge(sliceClones[x], dests[x]));
                    edgeSet.add(getEdge(sliceClones[x], dests[(x + 1) % fizzAmount]));
                    edgeSetSet.add(edgeSet);
                    weights.add(new Integer(numDup2));
                }

                if(numSingle2 > 0) {
                    edgeSet = new LinkedList<InterSliceEdge>();
                    edgeSet.add(getEdge(sliceClones[x], dests[(x + 1) % fizzAmount]));
                    edgeSetSet.add(edgeSet);
                    weights.add(new Integer(numSingle2));
                }
		
                sliceClones[x].getTail().setWeights(toArraySingleInt(weights));
                sliceClones[x].getTail().setDests(toArrayDouble(edgeSetSet));
            }

            // Generate steady-state joiner schedule for destination Slice
            edgeSet = new LinkedList<InterSliceEdge>();
            weights = new LinkedList<Integer>();

            edgeSet.add(getEdge(sliceClones[0], dests[0]));
            weights.add(new Integer((destPopMult * destPop) + (destPeek - destPop) - destCopyDown));

            if(destCopyDown > 0) {
                edgeSet.add(getEdge(sliceClones[fizzAmount - 1], dests[0]));
                weights.add(new Integer(destCopyDown));
            }

            sliceClones[0].getHead().setWeights(toArraySingleInt(weights));
            sliceClones[0].getHead().setSources(toArraySingle(edgeSet));

            for(int x = 1 ; x < fizzAmount ; x++) {
                edgeSet = new LinkedList<InterSliceEdge>();
                weights = new LinkedList<Integer>();

                if(destCopyDown > 0) {
                    edgeSet.add(getEdge(sliceClones[(x + fizzAmount - 1) % fizzAmount], dests[x]));
                    weights.add(new Integer(destCopyDown));
                }

                edgeSet.add(getEdge(sliceClones[x], dests[x]));
                weights.add(new Integer((destPopMult * destPop) + (destPeek - destPop) - destCopyDown));

                sliceClones[x].getHead().setWeights(toArraySingleInt(weights));
                sliceClones[x].getHead().setSources(toArraySingle(edgeSet));
            }
        }
        else {
            /*
             * Multiple destinations Slices, assume they come from a fizzed Slice
             * Unfortunately, there aren't fizzAmount destination slices, which is
             *     a case we can't presently handle
             *
             * NOTE: Shouldn't actually get here, canFizz() should have caught
             *     this already
             */
	    
            System.out.println("Can't fizz Slice because downstream Slice " +
                               "was not fizzed by the same amount");
            return false;
        }

        /**********************************************************************
         *               Roll steady-state multiplicity into loop             *
         **********************************************************************/

        /*
         * To assist code generation, the steady-state multiplicity is rolled
         * into a loop around the work body of each Slice clone.  The steady-
         * state multiplicity of each Slice clone is then set to 1.  
         *
         * The amount of work completed in each execution of a Slice clone stays
         * constant through this transform.  The main benefit of this transform
         * is that code can now be added to execute at the end of each steady-
         * state iteration.
         *
         * This capability is needed in order to remove unneeded elements from
         * each Slice clone at the end of every steady-state iteration.
         *
         * Unfortunately, this transform breaks the initialization multiplicity.
         * Fortunately, the initialization multiplicity is no longer needed at
         * this point since the initialization work has been copied into
         * prework.
         */

        // Get the work body for each Slice
        JBlock origWorkBodies[] = new JBlock[fizzAmount];

        for(int x = 0 ; x < fizzAmount ; x++)
            origWorkBodies[x] =
                sliceClones[x].getFirstFilter().getFilter().getWork().getBody();

        // Roll the steady-state multiplicity into a loop around the work
        // body of each Slice.
        for(int x = 0 ; x < fizzAmount ; x++) {

            // Construct new work body
            JBlock newWorkBody = new JBlock();

            // Add declaration for for-loop counter variable
            JVariableDefinition steadyMultLoopVar =
                new JVariableDefinition(0, 
                                        CStdType.Integer,
                                        "steadyMultCount",
                                        new JIntLiteral(0));

            JVariableDeclarationStatement steadyMultLoopVarDecl = new JVariableDeclarationStatement(steadyMultLoopVar);
            newWorkBody.addStatement(steadyMultLoopVarDecl);

            // Add for-loop that wraps around existing work body
            JRelationalExpression steadyMultLoopCond =
                new JRelationalExpression(JRelationalExpression.OPE_LT,
                                          new JLocalVariableExpression(steadyMultLoopVar),
                                          new JIntLiteral(sliceSteadyMult));

            JExpressionStatement steadyMultLoopIncr = 
                new JExpressionStatement(new JAssignmentExpression(new JLocalVariableExpression(steadyMultLoopVar),
                                                                   new JAddExpression(new JLocalVariableExpression(steadyMultLoopVar),
                                                                                      new JIntLiteral(1))));

            JForStatement steadyMultLoop =
                new JForStatement(new JEmptyStatement(),
                                  steadyMultLoopCond,
                                  steadyMultLoopIncr,
                                  (JBlock)ObjectDeepCloner.deepCopy(origWorkBodies[x]));
            newWorkBody.addStatement(steadyMultLoop);

            // Set new work body
            sliceClones[x].getFirstFilter().getFilter().getWork().setBody(newWorkBody);
        }

        // Now that steady-state multiplicity has been rolled around the work
        // bodies of the Slices, change steady-state multiplicity to 1.
        // Recalculate new Slice rates given new steady-state multiplicity.
            
        slicePeek = slicePop * sliceSteadyMult + slicePeek - slicePop;
        slicePop = slicePop * sliceSteadyMult;
        slicePush = slicePush * sliceSteadyMult;

        for(int x = 0 ; x < fizzAmount ; x++) {
            sliceClones[x].getFirstFilter().getFilter().setSteadyMult(1);
            sliceClones[x].getFirstFilter().getFilter().getWork().setPeek(slicePeek);
            sliceClones[x].getFirstFilter().getFilter().getWork().setPop(slicePop);
            sliceClones[x].getFirstFilter().getFilter().getWork().setPush(slicePush);
        }
        
        /**********************************************************************
         *                 Perform fission hacks on Slice rates               *
         **********************************************************************/
        
        // Normally, Slices remember peek - pop elements between steady-state
        // iterations.  However, after fizzing, these elements no longer need to
        // be remembered between iterations.  These elements therefore need to 
        // be removed at the end of each steady-state iteration
        //
        // This code adds a pop statement to the end of each work body, removing
        // the unneeded peek - pop elements.  The code also adjusts the pop rate
        // to reflect that more elements are being popped.

        if(slicePeek - slicePop > 0) {
            // Add pop statement to end of each work body
            for(int x = 0 ; x < fizzAmount ; x++) {
                CType inputType = 
                    sliceClones[x].getFirstFilter().getFilter().getInputType();
                
                SIRPopExpression popExpr =
                    new SIRPopExpression(inputType, slicePeek - slicePop);
                JExpressionStatement popStmnt =
                    new JExpressionStatement(popExpr);
                
                sliceClones[x].getFirstFilter().getFilter().getWork().getBody()
                    .addStatement(popStmnt);
            }

            // Adjust pop rates since more elements are now popped
            slicePop += (slicePeek - slicePop);

            for(int x = 0 ; x < fizzAmount ; x++)
                sliceClones[x].getFirstFilter().getFilter().getWork().setPop(slicePop);
        }

        // This is a hack to maintain the meaning of the peek rate.  Normally,
        // Slices remember peek - pop elements between steady-state iterations.
        // The first Slice clone will now remember sliceCopyDown elements 
        // between steady-state iterations (even after the above pop statement).
        // Therefore, we would like to set peek - pop to equal sliceCopyDown.
        //
        // At this point, peek equals pop.  Therefore, to set peek - pop to
        // equal sliceCopyDown, we simply increment peek by sliceCopyDown.

        if(sliceCopyDown > 0) {
            int newPeek = 
                sliceClones[0].getFirstFilter().getFilter().getWork().getPeekInt() +
                sliceCopyDown;

            sliceClones[0].getFirstFilter().getFilter().getWork().setPeek(newPeek);
        }

        /**********************************************************************
         *                              Finish up                             *
         **********************************************************************/

        // Add cloned Slices to HashMap.  Map each clone to the entire set of
        // cloned Slices.  This helps remember which set of cloned Slices a
        // Slice belongs to.

        LinkedList<Slice> cloneList = new LinkedList<Slice>();
        for(int x = 0 ; x < fizzAmount ; x++)
            cloneList.add(sliceClones[x]);

        for(int x = 0 ; x < fizzAmount ; x++)
            sliceToFizzedCopies.put(sliceClones[x], cloneList);

        return true;
    }
}
