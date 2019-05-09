package is.ru.hannes;

import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

public class RoleMovePair
{
    private Role role;
    private Move move;

    public RoleMovePair(Role role, List<Move> moves)
    {
        this.role = role;
        this.move = move;
    }

    public Role getRole()
    {
        return this.role;
    }
    public Move getMove()
    {
        return this.move;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(role, move);
    }
    
}