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
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;

import java.util.List;

public class ApprenticePolicy
{
    private double[] weights;
    private StateMachine machine;
    private Prover prover;
    private List<Gdl> gameRules;
    private MachineState state;

    public ApprenticePolicy(StateMachine machine, int numFeatures, List<Gdl> gameRules)
    {
        this.machine = machine;
        weights = new double[numFeatures];
        this.gameRules = gameRules;
        prover = new AimaProver(gameRules);
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
        System.out.println(rel);
        System.out.println("relation is " + prover.prove(rel, state.getContents()));
        return prover.prove(rel, state.getContents());
    }

    private boolean breakthroughIsCellOnEdge(MachineState state, Gdl x, Gdl y, Role p)
    {
        System.out.println("x: " + x);
        System.out.println("y: " + y);

        if (p.equals("white"))
        {
            return (y.toString().equals("8"));
        }
        else
        {
            return (y.toString().equals("1"));
        }
    }

    private boolean areBottomDiagonalsP(MachineState state, Gdl x, Gdl y, Role p)
    {
        Gdl xMinus1 = GdlPool.getConstant(String.valueOf(Integer.parseInt(x.toString()) - 1));
        Gdl xPlus1 = GdlPool.getConstant(String.valueOf(Integer.parseInt(x.toString()) + 1));
        Gdl yPlus1 = GdlPool.getConstant(String.valueOf(Integer.parseInt(y.toString()) + 1));
        System.out.println("x - 1: " + xMinus1);
        System.out.println("x + 1: " + xPlus1);
        System.out.println("y + 1: " + yPlus1);
        return breakthroughIsCellOwnedByRole(state, xMinus1, yPlus1, p) || breakthroughIsCellOwnedByRole(state, xPlus1, yPlus1, p);
    }

    private boolean areTopDiagonalsP(MachineState state, Gdl x, Gdl y, Role p)
    {
        Gdl xMinus1 = GdlPool.getConstant(String.valueOf(Integer.parseInt(x.toString()) - 1));
        Gdl xPlus1 = GdlPool.getConstant(String.valueOf(Integer.parseInt(x.toString()) + 1));
        Gdl yMinus1 = GdlPool.getConstant(String.valueOf(Integer.parseInt(y.toString()) - 1));
        System.out.println("x - 1: " + xMinus1);
        System.out.println("x + 1: " + xPlus1);
        System.out.println("y - 1: " + yMinus1);
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

    public double[] breakthroughFeatureVectorForStateActionPair(MachineState state, List<Move> move, Role role)
    {
        double[] featureVector = new double[4];

        // if our role is 0, enemyRole is 1, and vice versa
        Role enemyRole = machine.getRoles().get((machine.getRoleIndices().get(role) + 1) % 2);
        Move roleMove = move.get(machine.getRoleIndices().get(role));
        Gdl roleMoveContents = roleMove.getContents();

        System.out.println("inside checkersFeatureVectorForStateActionPair");
        System.out.println("roleMoveContents instanceof GdlFunction: " + (roleMoveContents instanceof  GdlFunction));
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
                System.out.println("feature for victory is set");
                featureVector[1] = 1.0;
            }

            if (rmcfName.toString().equals("move") && breakthroughIsCellAttackableByRole(state, destX, destY, role, enemyRole))
            {
                featureVector[2] = 1.0;
            }
        }


        // Can be captured


        System.out.println("feature vector:");
        for (int i = 0; i < 4; i++)
        {
            System.out.println(featureVector[i]);
        }
        return featureVector;
    }


}