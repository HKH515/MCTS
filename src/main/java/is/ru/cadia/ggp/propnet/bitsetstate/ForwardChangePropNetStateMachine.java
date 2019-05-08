package is.ru.cadia.ggp.propnet.bitsetstate;

import is.ru.cadia.ggp.propnet.PropNetMove;
import is.ru.cadia.ggp.propnet.structure.PropNetStructure;
import is.ru.cadia.ggp.propnet.structure.PropNetStructureFactory;
import is.ru.cadia.ggp.propnet.structure.components.BaseProposition;
import is.ru.cadia.ggp.propnet.structure.components.StaticComponent;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;

/**
 * propagate changes of components' values forward through the network
 *
 * the idea is to use the "computed" bit in a state to mean whether a component's
 * value has been computed and the output components have been marked as potentially changed (by
 * clearing their computed bits)
 *
 * This class works best if the components in the PropNetStructure are topologically ordered, that is
 * c.id < c.outputs[i].id (for all c,i). In case the propnet is cyclic this is not always possible.
 */
public class ForwardChangePropNetStateMachine extends PropNetBitSetStateMachine {

	/**
	 * all components that terminal depends on
	 */
	private BitSet terminalComponentSet;
	/**
	 * all components that any goal(x,y) depends on
	 */
	private BitSet goalComponentSet;
	/**
	 * all components that any legal(x,y) depends on
	 */
	private BitSet legalComponentSet;
	/**
	 * all components that any next(x) depends on
	 */
	private BitSet nextComponentSet;

	private int terminalComputedBit;
	private int goalComputedBit;
	private int legalComputedBit;

	private ForwardPropagationStrategy propagationStrategy;

	public ForwardChangePropNetStateMachine(PropNetStructureFactory factory) {
		super(factory);
	}
	public ForwardChangePropNetStateMachine(PropNetStructure structure) {
		super(structure);
	}

	@Override
	public void initialize(PropNetStructure propNetStructure) {
		// compute sets of necessary components for legal, terminal, goal and next as BitSets
		propagationStrategy = new ForwardPropagationStrategy(propNetStructure);

		super.initialize(propNetStructure);

		terminalComponentSet = getTransitiveInputs(Collections.singleton(propNetStructure.getTerminalProposition()));

		Collection<StaticComponent> goalComponents = new LinkedList<>();
		for(int roleId = 0; roleId < nbRoles; roleId++) {
			goalComponents.addAll(Arrays.asList(propNetStructure.getGoalPropositions(roleId)));
		}
		goalComponentSet = getTransitiveInputs(goalComponents);

		Collection<StaticComponent> legalComponents = new LinkedList<>();
		for(int roleId = 0; roleId < nbRoles; roleId++) {
			PropNetMove[] moves = propNetStructure.getPossibleMoves(roleId);
			for (PropNetMove move : moves) {
				legalComponents.add(move.getLegalComponent());
			}
		}
		legalComponentSet = getTransitiveInputs(legalComponents);

		Collection<StaticComponent> nextComponents = new LinkedList<>();
		for (BaseProposition base : propNetStructure.getBasePropositions() ) {
			nextComponents.add(base.nextComponent);
		}
		nextComponentSet = getTransitiveInputs(nextComponents);

		terminalComputedBit = propNetStructure.getMaxComponentId() + 1;
		goalComputedBit = terminalComputedBit + 1;
		legalComputedBit = goalComputedBit + 1;
	}

	private BitSet getTransitiveInputs(Collection<StaticComponent> components) {
		BitSet result = new BitSet();
		Deque<StaticComponent> queue = new LinkedList<StaticComponent>(components);
		while (!queue.isEmpty()) {
			StaticComponent c = queue.poll();
			result.set(c.id);
			for (int input : c.inputs) {
				if (!result.get(input)) {
					result.set(input);
					queue.push(propNetStructure.getComponent(input));
				}
			}
		}
		return result;
	}

	@Override
	public InternalBitSetState getNextState(InternalBitSetState state, PropNetMove[] jointMove, boolean makeNewState) {
		InternalBitSetState nextState = super.getNextState(state, jointMove, makeNewState);
		nextState.computed.clear(terminalComputedBit);
		nextState.computed.clear(goalComputedBit);
		nextState.computed.clear(legalComputedBit);
		return nextState;
	}

	@Override
	public boolean isTerminal(InternalBitSetState state) {
		if (!state.computed.get(terminalComputedBit)) {
			propagationStrategy.propagateChanges(terminalComponentSet, state);
			state.computed.set(terminalComputedBit);
		}
		return state.get(propNetStructure.getTerminalProposition().id);
	}

	@Override
	protected void computeTransitions(InternalBitSetState state) {
		propagationStrategy.propagateChanges(nextComponentSet, state);
	}

	@Override
	protected void computeLegals(InternalBitSetState state, int roleId) {
		if (!state.computed.get(legalComputedBit)) {
			propagationStrategy.propagateChanges(legalComponentSet, state);
			state.computed.set(legalComputedBit);
		}
	}
	@Override
	protected void computeGoal(InternalBitSetState state, int roleId) {
		if (!state.computed.get(goalComputedBit)) {
			propagationStrategy.propagateChanges(goalComponentSet, state);
			state.computed.set(goalComputedBit);
		}
	}
	@Override
	protected void changeBaseOrInputValue(StaticComponent c, InternalBitSetState state, boolean newValue) {
		propagationStrategy.setComponentValue(c, state, newValue);
	}

}
