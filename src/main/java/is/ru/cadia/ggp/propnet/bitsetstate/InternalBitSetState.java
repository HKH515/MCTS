package is.ru.cadia.ggp.propnet.bitsetstate;

import is.ru.cadia.ggp.propnet.InternalStateInterface;

import java.util.BitSet;

public class InternalBitSetState extends BitSet implements InternalStateInterface {

	/**
	 * bit is true if the corresponding component of the propNet has its value computed already
	 */
	protected BitSet computed;

	/**
	 * New states should always be created by the PropnetStateMachine
	 * to ensure that the state is consistent and the right computed bits are set.
	 */
	public InternalBitSetState() {
		super();
		computed = new BitSet();
	}

	/**
	 *
	 */
	private static final long serialVersionUID = 9147226235144837728L;

	public boolean isComputed(int id) {
		return computed.get(id);
	}

	public void setComputed(int id) {
		computed.set(id);
	}

	public void clearComputed(int id) {
		computed.clear(id);
	}

	public void resetComputed(BitSet computed) {
		this.computed = computed;
	}

	public BitSet getComputed() {
		return (BitSet)computed.clone();
	}

	@Override
	public InternalBitSetState clone() {
		InternalBitSetState s = (InternalBitSetState)super.clone();
		s.computed = (BitSet)this.computed.clone();
		return s;
	}

	public void setComputed(int startId, int endId) {
		computed.set(startId, endId);
	}

	public void clearComputed(int startId, int endId) {
		computed.clear(startId, endId);
	}

}
