package is.ru.hannes;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlFunction;
import org.ggp.base.util.prover.Prover;
import org.ggp.base.util.prover.aima.AimaProver;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.MachineState;

import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;

import java.util.List;

public class ApprenticePolicy
{
    private double[] weights;
    private StateMachine machine;
    private Prover prover;

    public ApprenticePolicy(StateMachine machine, int numFeatures)
    {
        this.machine = machine;
        weights = new double[numFeatures];
    }

    public double[] checkersFeatureVectorForStateActionPair(MachineState state, List<Move> move, Role role)
    {
        double[] featureVector = new double[4];
        
        Move roleMove = move.get(machine.getRoleIndices().get(role));
        Gdl roleMoveContents = roleMove.getContents();

        System.out.println("inside checkersFeatureVectorForStateActionPair");
        System.out.println("roleMoveContents instanceof GdlFunction: " + (roleMoveContents instanceof  GdlFunction));
        // Capturing
        if (roleMoveContents instanceof GdlFunction && ((GdlFunction)roleMoveContents).getName().toString() == "move")
        {
            if (machine.)
            featureVector[0] = 1.0;
        }

        // Promotion
        // Multi-capture 
        // Can be captured 


        return new double[]{};
    }


}