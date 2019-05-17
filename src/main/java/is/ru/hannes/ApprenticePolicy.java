package is.ru.hannes;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlFunction;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.gdl.grammar.GdlPool;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.prover.Prover;
import org.ggp.base.util.prover.aima.AimaProver;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.MachineState;

import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class ApprenticePolicy
{
    private double[] weights;
    private StateMachine machine;
    private Prover prover;
    private double learningRate;

    private List<Gdl> gameRules;
    private double lambda;

    ApprenticePolicy(StateMachine machine, int numFeatures, List<Gdl> gameRules)
    {
        this.machine = machine;
        this.gameRules = gameRules;
        lambda = 0.1;
        prover = new AimaProver(gameRules);
        weights = new double[numFeatures];
        learningRate = 0.1;

        Random rand = new Random();
        for (int i = 0; i < numFeatures; i++)
        {
            weights[i] = -0.01  + (0.02*rand.nextDouble());
        }
    }

    public void setLearningRate(double learningRate)
    {
        this.learningRate = learningRate;
    }

    private GdlRelation breakthroughIsCellOwnedByRoleRelation(Gdl x, Gdl y, Role p)
    {

        return GdlPool.getRelation(GdlPool.TRUE,
                new GdlTerm[] {
                        GdlPool.getFunction(
                                GdlPool.getConstant("cellHolds"),
                                new GdlTerm[] {
                                        GdlPool.getConstant(x.toString()),
                                        GdlPool.getConstant(y.toString()),
                                        GdlPool.getConstant(p.toString())
                                }
                        )
                });
    }

    private boolean breakthroughIsCellOwnedByRole(MachineState state, Gdl x, Gdl y, Role p)
    {
        GdlRelation rel = breakthroughIsCellOwnedByRoleRelation(x, y, p);
        //System.out.println(rel);
        return prover.prove(rel, state.getContents());
    }

    private boolean breakthroughIsCellOnEdge(MachineState state, Gdl x, Gdl y, Role p)
    {
        //System.out.println("x: " + x);
        //System.out.println("y: " + y);

        if (p.toString().equals("white"))
        {
            return (y.toString().equals("8"));
        }
        else
        {
            return (y.toString().equals("1"));
        }
    }

    /*private char increaseChar(String chars)
    {
        char ch = chars.charAt(0);
        ch++;
        return ch;
    }
    private char decreaseChar(String chars)
    {
        char ch = chars.charAt(0);
        ch--;
        return (char)ch;
    }*/

    private boolean areBottomDiagonalsP(MachineState state, Gdl x, Gdl y, Role p)
    {
        Gdl xMinus1 = GdlPool.getConstant(String.valueOf(Integer.parseInt(x.toString()) - 1));
        Gdl xPlus1 = GdlPool.getConstant(String.valueOf(Integer.parseInt(x.toString()) + 1));
        Gdl yPlus1 = GdlPool.getConstant(String.valueOf(Integer.parseInt(y.toString()) + 1));
        //System.out.println("x - 1: " + xMinus1);
        //System.out.println("x + 1: " + xPlus1);
        //System.out.println("y + 1: " + yPlus1);
        return breakthroughIsCellOwnedByRole(state, xMinus1, yPlus1, p) || breakthroughIsCellOwnedByRole(state, xPlus1, yPlus1, p);
    }

    private boolean areTopDiagonalsP(MachineState state, Gdl x, Gdl y, Role p)
    {
        Gdl xMinus1 = GdlPool.getConstant(String.valueOf(Integer.parseInt(x.toString()) - 1));
        Gdl xPlus1 = GdlPool.getConstant(String.valueOf(Integer.parseInt(x.toString()) + 1));
        Gdl yMinus1 = GdlPool.getConstant(String.valueOf(Integer.parseInt(y.toString()) - 1));
        //System.out.println("x - 1: " + xMinus1);
        //System.out.println("x + 1: " + xPlus1);
        //System.out.println("y - 1: " + yMinus1);
        return breakthroughIsCellOwnedByRole(state, xMinus1, yMinus1, p) || breakthroughIsCellOwnedByRole(state, xPlus1, yMinus1, p);
    }

    private boolean breakthroughIsCellAttackableByRole(MachineState state, Gdl x, Gdl y, Role myRole, Role enemyRole)
    {
        if (myRole.equals("white"))
        {
            return areBottomDiagonalsP(state, x, y, enemyRole);
        }
        else
        {
            return areTopDiagonalsP(state, x, y, enemyRole);
        }
    }

    double[] breakthroughFeatureVectorForStateActionPair(MachineState state, List<Move> move, Role role)
    {
        double[] featureVector = new double[3];

        // if our role is 0, enemyRole is 1, and vice versa
        Role enemyRole = machine.getRoles().get((machine.getRoleIndices().get(role) + 1) % 2);
        Move roleMove = move.get(machine.getRoleIndices().get(role));
        Gdl roleMoveContents = roleMove.getContents();

        if (roleMoveContents instanceof GdlFunction)
        {
            // Capturing
            GdlFunction roleMoveContentsFunc = ((GdlFunction)roleMoveContents);
            GdlTerm rmcfName = roleMoveContentsFunc.getName();
            List<GdlTerm> rmcfBody = roleMoveContentsFunc.getBody();
            GdlTerm destX = rmcfBody.get(2);
            GdlTerm destY = rmcfBody.get(3);
            if (rmcfName.toString().equals("move") && breakthroughIsCellOwnedByRole(state, destX, destY, enemyRole))
            {
                featureVector[0] = 1.0;
            }

            // Is going to win
            if (rmcfName.toString().equals("move") && breakthroughIsCellOnEdge(state, destX, destY, role))
            {
                featureVector[1] = 1.0;
            }

            // Can be captured
            if (rmcfName.toString().equals("move") && breakthroughIsCellAttackableByRole(state, destX, destY, role, enemyRole))
            {
                featureVector[2] = 1.0;
            }
        }

        return featureVector;
    }

    private double f(double[] featureVector)
    {
        int length = featureVector.length;

        double sum = 0;

        for (int i = 0; i < length; ++i)
        {
            sum += featureVector[i] * weights[i];
        }

        return sum;
    }

    double[] computeProbabilities(List<List<Move>> legalMoves, List<double[]> featureVectors)
    {
        int size = legalMoves.size();
        double[] exponents = new double[size];

        for (int i = 0; i < legalMoves.size(); ++i)
        {
            exponents[i] = Math.exp(f(featureVectors.get(i)));
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

    double[] computeFeatureVector(MachineState state, List<Move> move, Role role)
    {
        return breakthroughFeatureVectorForStateActionPair(state, move, role);
    }

    void doGradientDescentUpdate(MCTSNode node, Role role, HashMap<RoleMovePair, double[]> cachedFeatures) throws MoveDefinitionException
    {
        List<List<Move>> legalMoves = machine.getLegalJointMoves(node.getState());
        double[] expertProbabilities = new double[legalMoves.size()];
        int idx = 0;

        List<double[]> featureVectors = new ArrayList<>();

        for (List<Move> legalMove : legalMoves)
        {
            RoleMovePair rmp = new RoleMovePair(role, legalMove);
            double[] featureVector = cachedFeatures.get(rmp);

            if (featureVector == null)
            {
                featureVector = breakthroughFeatureVectorForStateActionPair(node.getState(), legalMove, role);
            }

            featureVectors.add(featureVector);
            expertProbabilities[idx++] = node.getActionProbability(legalMove, role);
        }

        double[] moveProbabilities = computeProbabilities(legalMoves, featureVectors);

        int numFeatures = weights.length;

        double[] updateValues = new double[numFeatures];

        for (int i = 0; i < legalMoves.size(); ++i)
        {
            double[] sapFeatures = featureVectors.get(i);
            double probabilityError = moveProbabilities[i] - expertProbabilities[i];

            System.out.println("Feature vector size: " + sapFeatures.length);

            for (int j = 0; j < numFeatures; ++j)
            {
                updateValues[j] += (probabilityError * sapFeatures[j]);
            }
        }

        for (int i = 0; i < numFeatures; ++i)
        {
            updateValues[i] -= learningRate * lambda * weights[i];
            updateValues[i] *= learningRate;
            weights[i] -= updateValues[i];
        }

        for (double i : weights){
            System.out.println(i);
        }
        System.out.println("");
    }

}