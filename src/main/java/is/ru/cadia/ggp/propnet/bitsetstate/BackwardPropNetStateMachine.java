package is.ru.cadia.ggp.propnet.bitsetstate;

import is.ru.cadia.ggp.propnet.PropNetMove;
import is.ru.cadia.ggp.propnet.structure.PropNetStructure;
import is.ru.cadia.ggp.propnet.structure.PropNetStructureFactory;
import is.ru.cadia.ggp.propnet.structure.components.BaseProposition;
import is.ru.cadia.ggp.propnet.structure.components.StaticComponent;

import java.util.BitSet;

import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;

public class BackwardPropNetStateMachine extends PropNetBitSetStateMachine {

	public BackwardPropNetStateMachine(PropNetStructure propNetStructure) {
		super(propNetStructure);
	}

	public BackwardPropNetStateMachine(PropNetStructureFactory factory) {
		super(factory);
	}

	@Override
	public int getGoal(InternalBitSetState state, int roleId) throws GoalDefinitionException {
		StaticComponent[] goals = propNetStructure.getGoalPropositions(roleId);
		int[] goalValues = propNetStructure.getGoalValues(roleId);
		for (int i = 0; i < goals.length; i++) {
			if (compute(goals[i].id, state)) {
				return goalValues[i];
			}
		}
		throw new GoalDefinitionException(internalStateToMachineState(state), this.getRoles().get(roleId));
	}

	@Override
	public boolean isTerminal(InternalBitSetState state) {
		return compute(propNetStructure.getTerminalProposition().id, state);
	}

	@Override
	protected void computeTransitions(InternalBitSetState state) {
		for (BaseProposition p : propNetStructure.getBasePropositions()) {
			compute(p.nextComponent.id, state);
		}
	}

	@Override
	protected void computeLegals(InternalBitSetState state, int roleId) {
		for (PropNetMove p : propNetStructure.getPossibleMoves(roleId)) {
			compute(p.getLegalComponent().id, state);
		}
	}

	@Override
	protected void setInputs(InternalBitSetState state, PropNetMove[] jointMove) {
		super.setInputs(state, jointMove);
		state.resetComputed(getBaseStateComputed());
	}

	@Override
	public InternalBitSetState getNextState(InternalBitSetState state, PropNetMove[] jointMove, boolean makeNewState) {
		InternalBitSetState nextState = super.getNextState(state, jointMove, makeNewState);
		nextState.resetComputed(getBaseStateComputed());
		return nextState;
	}

	private boolean compute(int cId, InternalBitSetState state) {
		if (propNetStructure.isCylic()) {
			BitSet visiting = new BitSet(propNetStructure.getNbComponents());
			return (computeCyclic(cId, state, visiting) & ISTRUE) == ISTRUE;
		} else {
			return computeAcyclic(cId, state);
		}
	}

	static final int ISTRUE = 0x1;
	static final int ISCOMPUTED = 0x2;

	// this may take time exponential in the number of components in the cycle because it may check every single path through the cyclic components

	private int computeCyclic(int cId, InternalBitSetState state, BitSet visiting) {
		if (state.isComputed(cId)) return (state.get(cId) ? ISTRUE : 0) | ISCOMPUTED;
		StaticComponent c = propNetStructure.getComponent(cId);
		if (c.isCyclic) {
			if (visiting.get(cId)) return 0; // breaking cycles
			visiting.set(cId);
		}
		int value;
		switch (c.type) {
			case TRUE:
				value = ISTRUE | ISCOMPUTED;
				break;
			case FALSE:
				value = ISCOMPUTED;
				break;
			case AND:
				value = ISTRUE | ISCOMPUTED;
				for(int input:c.inputs) {
					int iValue = computeCyclic(input, state, visiting);
					// if the value is true, it counts as computed if all input values are computed
					value = value & iValue;
					if ((value & ISTRUE) == 0) {
						// if the value is false, it counts as computed if any false input value is computed
						// Note: iValue is false here and the ISCOMPUTED bit in value is the conjunction of all
						// computed bits of inputs so far
						value = value | iValue;
						break;
					}
				}
				break;
			case OR:
				value = ISCOMPUTED;
				for(int input:c.inputs) {
					int iValue = computeCyclic(input, state, visiting);
					// if the value is false, it counts as computed if all input values are computed
					// that is we use & on the ISCOMPUTED bit and | on the ISTRUE bit
					value = (value & iValue) | (iValue & ISTRUE);
					if ((value & ISTRUE) == ISTRUE) {
						// if the value is true, it counts as computed if any true input value is computed
						// Note: iValue is true here and the ISCOMPUTED bit in value is the conjunction of all
						// computed bits of inputs so far, including this one
						value = value | iValue;
						break;
					}
				}
				break;
			case NOT:
				value = computeCyclic(c.inputs[0], state, visiting);
				value ^= ISTRUE; // flip truth value
				break;
			case PIPE:
				value = computeCyclic(c.inputs[0], state, visiting);
				break;
			default:
				throw new RuntimeException("Can't compute value of StaticComponent of type " + c.type);
		}
		// For cyclic components, only the first component in the cycle we
		// looked at is guaranteed to have the correct value now.
		// For other components in the cycle we computed the value under the assumption,
		// that the first one is false and the value might depend on this assumption (if the ISCOMPUTED bit is 0).
		if ((value & ISCOMPUTED) == ISCOMPUTED) {
			state.set(cId, (value & ISTRUE) == ISTRUE);
			state.setComputed(cId);
		} else if (c.isCyclic) {
			visiting.clear(cId);
			if (visiting.isEmpty()) {
				state.set(cId, (value & ISTRUE) == ISTRUE);
				state.setComputed(cId);
				value |= ISCOMPUTED;
			}
		} else {
			throw new RuntimeException("a noncyclic component whose value could not be exactly determined should not exist!\n" + c);
		}
		return value;
	}

	private boolean computeAcyclic(int cId, InternalBitSetState state) {
		if (state.isComputed(cId)) return state.get(cId);
		StaticComponent c = propNetStructure.getComponent(cId);
		boolean value;
		switch (c.type) {
			case TRUE:
				value = true;
				break;
			case FALSE:
				value = false;
				break;
			case AND:
				value = true;
				for(int input:c.inputs) {
					value = value && computeAcyclic(input, state);
					if (!value) break;
				}
				break;
			case OR:
				value = false;
				for(int input:c.inputs) {
					value = value || computeAcyclic(input, state);
					if (value) break;
				}
				break;
			case NOT:
				value = !computeAcyclic(c.inputs[0], state);
				break;
			case PIPE:
				value = computeAcyclic(c.inputs[0], state);
				break;
			default:
				throw new RuntimeException("Can't compute value of StaticComponent of type " + c.type);
		}
		state.set(cId, value);
		state.setComputed(cId);
		return value;
	}

	@Override
	protected void computeGoal(InternalBitSetState state, int roleId) {
		// nothing to do here, because work is done in getGoal(state, roleId)
	}

	@Override
	protected void changeBaseOrInputValue(StaticComponent c, InternalBitSetState state, boolean newValue) {
		state.set(c.id, newValue);
	}

}
