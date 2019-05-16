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

    private GdlRelation isCellOwnedByPRelation(Gdl x, Gdl y, Role p)
    {

        return GdlPool.getRelation(GdlPool.TRUE,
                new GdlTerm[] {
                        GdlPool.getFunction(
                                GdlPool.getConstant("cell"),
                                new GdlTerm[] {
                                        GdlPool.getConstant(x.toString()),
                                        GdlPool.getConstant(y.toString()),
                                        GdlPool.getConstant(p.toString())
                                }
                        )
                });
    }

    private boolean isCellOwnedByP(MachineState state, Gdl x, Gdl y, Role p)
    {
        return prover.prove(isCellOwnedByPRelation(x, y, p), state.getContents());
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
        // Capturing
        if (roleMoveContents instanceof GdlFunction)
        {
            GdlFunction roleMoveContentsFunc = ((GdlFunction)roleMoveContents);
            GdlTerm rmcfName = roleMoveContentsFunc.getName();
            List<GdlTerm> rmcfBody = roleMoveContentsFunc.getBody();
            System.out.println("rmcfBody: " + rmcfBody);
            GdlTerm destX = rmcfBody.get(2);
            GdlTerm destY = rmcfBody.get(3);
            if (rmcfName.toString().equals("move") && isCellOwnedByP(state, destX, destY, enemyRole))
            {
                System.out.println("capture");
                featureVector[0] = 1.0;
            }
        }

        System.out.println("feature vector:");
        for (int i = 0; i < 4; i++)
        {
            System.out.println(featureVector[i]);
        }

        // Promotion
        // Multi-capture 
        // Can be captured 


        return new double[]{};
    }


}