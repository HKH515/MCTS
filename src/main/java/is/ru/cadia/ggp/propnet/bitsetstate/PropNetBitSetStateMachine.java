package is.ru.cadia.ggp.propnet.bitsetstate;

import is.ru.cadia.ggp.propnet.PropNetMove;
import is.ru.cadia.ggp.propnet.statemachine.PropNetStateMachine;
import is.ru.cadia.ggp.propnet.structure.PropNetStructure;
import is.ru.cadia.ggp.propnet.structure.PropNetStructureFactory;
import is.ru.cadia.ggp.propnet.structure.components.StaticComponent;

import java.util.BitSet;

public abstract class PropNetBitSetStateMachine extends PropNetStateMachine<InternalBitSetState> {

	public PropNetBitSetStateMachine(PropNetStructureFactory factory) {
		super(factory);
	}

	public PropNetBitSetStateMachine(PropNetStructure propNetStructure) {
		super(propNetStructure);
	}

    /**
     *  returns a state with:
     *  - the computed bits for all components that have fixed values
     *    being set (including base and inputs)
     *  - the values of all constant components being set correctly
     *  - everything else being false
     */
	@Override
	protected InternalBitSetState makeBaseState() {
		InternalBitSetState baseState = new InternalBitSetState();
		baseState.computed.set(0, propNetStructure.getNbBasePropositions());
		for (StaticComponent c : propNetStructure.getComponents()) {
			switch (c.type) {
				case TRUE:
				case FALSE:
				case INPUT:
					baseState.computed.set(c.id);
					break;
				default:
					break;
			}
		}
		for (StaticComponent c : propNetStructure.getComponents()) {
			switch (c.type) {
				case TRUE:
					baseState.set(c.id);
					break;
				default:
					break;
			}
		}
		return baseState;
	}

	protected BitSet getBaseStateComputed() {
		return getAnEmptyState().computed;
	}

	@Override
	protected void setInputs(InternalBitSetState state, PropNetMove[] jointMove) {
		BitSet bitsToUnSet = (BitSet)inputComponentSet.clone();
		bitsToUnSet.and(state);
		for (PropNetMove move : jointMove) {
			int inputId = move.getInputComponent().id;
			if (bitsToUnSet.get(inputId)) {
				bitsToUnSet.clear(inputId);
			} else {
				changeBaseOrInputValue(move.getInputComponent(), state, true);
			}
		}
		for (int nextId = bitsToUnSet.nextSetBit(0); nextId != -1; nextId = bitsToUnSet.nextSetBit(nextId+1)) {
			changeBaseOrInputValue(propNetStructure.getComponent(nextId), state, false);
		}
	}
}
