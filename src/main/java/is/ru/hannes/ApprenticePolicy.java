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
    private double learningRate;

    ApprenticePolicy(StateMachine machine, int numFeatures)
    {
        this.machine = machine;
        weights = new double[numFeatures];
    }

    public void setLearningRate(double learningRate)
    {
        this.learningRate = learningRate;
    }

    double[] checkersFeatureVectorForStateActionPair(MachineState state, List<Move> move, Role role)
    {
        double[] featureVector = new double[4];
        
        Move roleMove = move.get(machine.getRoleIndices().get(role));
        Gdl roleMoveContents = roleMove.getContents();

        System.out.println("inside checkersFeatureVectorForStateActionPair");
        System.out.println("roleMoveContents instanceof GdlFunction: " + (roleMoveContents instanceof  GdlFunction));
        // Capturing
        if (roleMoveContents instanceof GdlFunction && ((GdlFunction)roleMoveContents).getName().toString() == "move")
        {
            //if (machine.)
            featureVector[0] = 1.0;
        }

        // Promotion
        // Multi-capture 
        // Can be captured 


        return new double[]{};
    }

    private double[] computeProbabilities(MachineState state, Role role) throws Exception
    {
        List<List<Move>> legalMoves = machine.getLegalJointMoves(state);
        int size = legalMoves.size();
        double[] exponents = new double[size];
        int idx = 0;

        for (List<Move> playerMoves : legalMoves)
        {
            exponents[idx++] = Math.exp(breakthroughFeatureVectorForStateActionPair(state, playerMoves, role));
        }

        double expSum = 0.0;
        for (double exp : exponents)
        {
            expSum += exp;
        }

        double[] probabilities = new double[size];
        for (int i = 0; i < size; ++i)
        {
            probabilities[i] = exponents[i]/expSum;
        }

        return probabilities;
    }

    public void doGradientDescentUpdate(MCTSNode node)
    {

    }

}