package hlt;

import java.util.ArrayList;
import java.util.List;

public class GameMap {
    public final int width;
    public final int height;
    public final MapCell[][] cells;

    public GameMap(final int width, final int height) {
        this.width = width;
        this.height = height;

        cells = new MapCell[height][];
        for (int y = 0; y < height; ++y) {
            cells[y] = new MapCell[width];
        }
    }

    public MapCell at(final Position position) {
        final Position normalized = normalize(position);
        return cells[normalized.y][normalized.x];
    }

    public MapCell at(final Entity entity) {
        return at(entity.position);
    }

    public int calculateDistance(final Position source, final Position target) {
        final Position normalizedSource = normalize(source);
        final Position normalizedTarget = normalize(target);

        final int dx = Math.abs(normalizedSource.x - normalizedTarget.x);
        final int dy = Math.abs(normalizedSource.y - normalizedTarget.y);

        final int toroidal_dx = Math.min(dx, width - dx);
        final int toroidal_dy = Math.min(dy, height - dy);

        return toroidal_dx + toroidal_dy;
    }

    public Position normalize(final Position position) {
        final int x = ((position.x % width) + width) % width;
        final int y = ((position.y % height) + height) % height;
        return new Position(x, y);
    }

    public ArrayList<Direction> getUnsafeMoves(final Position source, final Position destination) {
        final ArrayList<Direction> possibleMoves = new ArrayList<>();

        final Position normalizedSource = normalize(source);
        final Position normalizedDestination = normalize(destination);

        final int dx = Math.abs(normalizedSource.x - normalizedDestination.x);
        final int dy = Math.abs(normalizedSource.y - normalizedDestination.y);
        final int wrapped_dx = width - dx;
        final int wrapped_dy = height - dy;

        if (normalizedSource.x < normalizedDestination.x) {
            possibleMoves.add(dx > wrapped_dx ? Direction.WEST : Direction.EAST);
        } else if (normalizedSource.x > normalizedDestination.x) {
            possibleMoves.add(dx < wrapped_dx ? Direction.WEST : Direction.EAST);
        }

        if (normalizedSource.y < normalizedDestination.y) {
            possibleMoves.add(dy > wrapped_dy ? Direction.NORTH : Direction.SOUTH);
        } else if (normalizedSource.y > normalizedDestination.y) {
            possibleMoves.add(dy < wrapped_dy ? Direction.NORTH : Direction.SOUTH);
        }

        return possibleMoves;
    }

    public Direction naiveNavigate(final Ship ship, final Position destination, Player player, boolean late) {
        // getUnsafeMoves normalizes for us
        for (final Direction direction : getUnsafeMoves(ship.position, destination)) {
            final Position targetPos = ship.position.directionalOffset(direction);
            if (!at(targetPos).isOccupied()) {
                at(targetPos).markUnsafe(ship);
                return direction;
            } else {
                // if the cell is occupied by a friendly shipyard/dropoff, and there is an enemy ship on
                // the cell, direct the current ship to collide the enemy(this way we lose one ship, but
                // we will destroy an enemy and keep the halite). Otherwise, if the ship on the structure
                // is ours, we will destroy it only(depending on map configuration) we receive the signal
                // that the game has reached late game stage.
                if (at(targetPos).hasStructure()) {
                    if (at(targetPos).structure.owner == player.id) {
                        if (at(targetPos).ship.owner != player.id) {
                            at(targetPos).markUnsafe(ship);
                            return direction;
                        } else {
                            if (late == true) {
                                return direction;
                            }
                        }
                    }
                }
            }
        }

        return Direction.STILL;
    }

    void _update() {
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                cells[y][x].ship = null;
            }
        }

        final int updateCount = Input.readInput().getInt();

        for (int i = 0; i < updateCount; ++i) {
            final Input input = Input.readInput();
            final int x = input.getInt();
            final int y = input.getInt();

            cells[y][x].halite = input.getInt();
        }
    }

    static GameMap _generate() {
        final Input mapInput = Input.readInput();
        final int width = mapInput.getInt();
        final int height = mapInput.getInt();

        final GameMap map = new GameMap(width, height);

        for (int y = 0; y < height; ++y) {
            final Input rowInput = Input.readInput();

            for (int x = 0; x < width; ++x) {
                final int halite = rowInput.getInt();
                map.cells[y][x] = new MapCell(new Position(x, y), halite);
            }
        }

        return map;
    }
}
