package is.ru.cadia.ggp.propnet;

public interface InternalStateInterface {
	public Object clone();

	/**
	 * get the value of a propnet component in this state

	 * does not necessarily ensure that the returned value is up-to-date
	 * @param id
	 * @return
	 */
	public boolean get(int id);
}
