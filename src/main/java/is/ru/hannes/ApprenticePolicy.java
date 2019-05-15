package is.ru.hannes;

import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.MachineState;

import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;

import java.util.List;

public class ApprenticePolicy
{
    private double[] weights;
    private StateMachine machine;

    public ApprenticePolicy(StateMachine machine, int numFeatures)
    {
        this.machine = machine;
        weights = new double[numFeatures];
    }

    private double[] checkersFeatureVectorForStateActionPair(MachineState state, List<Move> move, Role role)
    {
        double[] featureVector = new double[4];
        
        Move roleMove = move.get(machine.getRoleIndices().get(role));

        // Capturing
        // Promotion
        // Multi-capture 
        // Can be captured 


        return new double[]{};
    }


}