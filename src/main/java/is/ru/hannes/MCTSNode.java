package is.ru.hannes;

import java.util.LinkedList;
import java.util.List;
import org.ggp.base.util.statemachine.MachineState;

public class MCTSNode 
{
    List<MCTSNode> children;
    
    public void MCTSNode() 
    {
        children = new LinkedList<MCTSNode>();
    }

    public void addChild(MachineState s) 
    {
        
    }
}
