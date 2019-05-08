package is.ru.hannes;

import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

public class RoleMovePair
{
    private Role role;
    private List<Move> moves;

    public RoleMovePair(Role role, List<Move> moves)
    {
        this.role = role;
        this.moves = new ArrayList<>(moves);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(role, moves);
    }
    
}