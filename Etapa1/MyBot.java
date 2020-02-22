// This Java API uses camelCase instead of the snake_case as documented in the API docs.
//     Otherwise the names of methods are consistent.

import hlt.*;

import java.util.*;

class Pair<T> {
    public T first;
    public T second;

    public Pair(final T first, final T second) {
        this.first = first;
        this.second = second;
    }
}

public class MyBot {
    static final int SQUARE_LENGTH = 10;
    // check if a certain position is safe; the position is safe if it is not surrounded by any ships
    static boolean safePosition(Position checkedPosition, Position shipPosition,
                                GameMap gameMap, Player me) {
        List<Position> cardinals = checkedPosition.getSurroundingCardinals();

        for (Position p: cardinals) {
            // normalize position to respect map bounds
            p = gameMap.normalize(p);

            // for the checked position verify if surrounding positions are free and different from current ship position
            if (!p.equals(shipPosition) && gameMap.at(p).isOccupied()) {
               return false;
            }
        }
        return true;
    }

    // function to calculate optimum dropoff area
    static Pair<Integer> haliteAndShipsAround(Position position, GameMap gameMap, Player me) {
        Pair<Integer> result = new Pair<>(0, 0);
        Position auxPosition = new Position(position.x - (SQUARE_LENGTH / 2), position.y - (SQUARE_LENGTH / 2));
        for(int i = 0; i < SQUARE_LENGTH; i++) {
            auxPosition.x += i;
            for (int j = 0; j < SQUARE_LENGTH; j++) {
                auxPosition.y += j;
                auxPosition = gameMap.normalize(auxPosition);
                result.first += gameMap.at(auxPosition).halite;
                if (gameMap.at(auxPosition).isOccupied() &&
                        gameMap.at(auxPosition).ship.owner == me.id) {
                    ++result.second;
                }
            }
        }
        --result.second;
        return result;
    }

    public static void main(final String[] args) {

        // limit number of dropoffs
        boolean droppoff = false;

        // maximum number of ships at a given turn
        int nrShips = 9;

        // if the ship has over TARGET_HALITE halite and is closer than SHIPYARD_DISTANCE to shipyard, it must deposit
        final int SHIPYARD_DISTANCE = 5;
        final int TARGET_HALITE = 800;

        // leaves halite for the ships returning to the dropoff to compensate movement cost
        final int MINIMUM_HALITE = 100;

        // minimium halite in an area necesarry to create dropoff
        final int HALITE_FOR_DROPOFF = 6000;

        // minimum number of ships in an area necesarry to create dropoff
        final int SHIPS_FOR_DROPOFF = 1;

        // checks that dropoff is not too close to shipyard
        final int MINIMUM_DISTANCE_FROM_SHIPYARD = 10;

        Game game = new Game();

        game.ready("MyJavaBot");

        // the four directions (N, S, E, W)
        List<Direction> directions = Direction.ALL_CARDINALS;

        // shipState = 'c' => ship collects halite
        // shipState = 'd' => ship has to deposit/store halite
        Map<Integer, Character> shipState = new HashMap<>();

        // maps collect instruction for the ships, after having found maximum halite cell
        Map<Integer, Integer> shouldCollect = new HashMap<>();

        for (;;) {
            game.updateFrame();
            final Player me = game.me;
            final GameMap gameMap = game.gameMap;

            final ArrayList<Command> commandQueue = new ArrayList<>();

            // for each turn contains chosen positions
            List<Position> occupiedPositions = new ArrayList<>();

            for (final Ship ship : me.ships.values()) {
                // if ship is new, the ship will have to collect halite
                if (!shipState.keySet().contains(ship.id.id)) {
                    shipState.put(ship.id.id, 'c');
                    shouldCollect.put(ship.id.id, 0);
                } else {
                    // check ship state
                    // if ship reaches maximum halite amount, then the ship has to deposit
                    if (ship.halite >= Constants.MAX_HALITE) {
                        shipState.put(ship.id.id, 'd');
                    } else {
                        if (ship.halite > TARGET_HALITE) {
                            if(!me.dropoffs.isEmpty()) {
                                // get nearest dropoff
                                Dropoff dropoff = me.dropoffs.values().iterator().next();
                                if(gameMap.calculateDistance(ship.position, dropoff.position) < SHIPYARD_DISTANCE){
                                    shipState.put(ship.id.id, 'd');
                                }

                            }
                            // or shipyard
                            if(gameMap.calculateDistance(ship.position, me.shipyard.position) < SHIPYARD_DISTANCE){
                                shipState.put(ship.id.id, 'd');
                            }
                        }
                    }
                }

                // if the ship needs to collect, select best position
                if (shipState.get(ship.id.id).equals('c')) {
                    if (shouldCollect.get(ship.id.id) == 1) {
                        commandQueue.add(ship.stayStill());
                        shouldCollect.put(ship.id.id, 0);
                    } else {
                        // get surrounding cardinals for a certain ship
                        List<Position> cardinals = ship.position.getSurroundingCardinals();

                        // create an association between direction and position
                        Map<Direction, Position> directionMap = new HashMap<>();
                        for (int i = 0; i < directions.size(); ++i) {
                            directionMap.put(directions.get(i), gameMap.normalize(cardinals.get(i)));
                        }

                        // each direction has a position where you can collect halite
                        Map<Direction, Integer> choices = new LinkedHashMap<>();
                        for (Direction d : directionMap.keySet()) {
                            // if the new position for this turn is not occupied
                            // and the new position is not occupied from the last turn
                            if (!occupiedPositions.contains(directionMap.get(d)) &&
                                    !gameMap.at(directionMap.get(d)).isOccupied()) {
                                // check if the position is safe
                                if (safePosition(directionMap.get(d), ship.position, gameMap, me) == true) {
                                    choices.put(d, gameMap.at(directionMap.get(d)).halite);
                                }
                            }
                        }
                        choices.put(Direction.STILL, gameMap.at(ship.position).halite);

                        // find optimum direction
                        Map.Entry<Direction, Integer> directionWithMaxHaliteAmount = Collections.max(choices.entrySet(), new Comparator<Map.Entry<Direction, Integer>>() {
                            @Override
                            public int compare(Map.Entry<Direction, Integer> o1, Map.Entry<Direction, Integer> o2) {
                                int res = o1.getValue().intValue() - o2.getValue().intValue();
                                if (res > 0) {
                                	return 1;
                                }

                                if (res < 0) {
                                	return -1;
                                }

                                return 0;
                            }
                        });

                        if (Direction.STILL.equals(directionWithMaxHaliteAmount.getKey())) {
                            commandQueue.add(ship.stayStill());
                        } else {
                            // calculate if it's worth continuing to collect halite, even if there's a cell with more halite
                            // next to the ship (considering ship's movement cost)
                            int a = (int)(ship.halite + 0.25 * gameMap.at(ship.position).halite);
                            int b = (int)(ship.halite - 0.1 * ship.halite + 0.25 * directionWithMaxHaliteAmount.getValue());
                            if (a >= b && gameMap.at(ship.position).halite > MINIMUM_HALITE) {
                                commandQueue.add(ship.stayStill());
                            } else {

                                // mark that position as occupied/chosen
                                occupiedPositions.add(directionMap.get(directionWithMaxHaliteAmount.getKey()));
                                commandQueue.add(ship.move(directionWithMaxHaliteAmount.getKey()));
                                shouldCollect.put(ship.id.id, 1);
                            }
                        }
                    }

                } else {
                    Pair<Integer> result = haliteAndShipsAround(ship.position, gameMap, me);
                    if (!droppoff  && me.halite >= Constants.DROPOFF_COST &&
                            gameMap.calculateDistance(ship.position, me.shipyard.position) > MINIMUM_DISTANCE_FROM_SHIPYARD &&
                            result.first >= HALITE_FOR_DROPOFF && result.second > SHIPS_FOR_DROPOFF) {
                        droppoff = true;
                        commandQueue.add(ship.makeDropoff());
                        --nrShips;
                    } else {
                        // go to closest dropoff/shipyard
                        int minDistance;
                        Position p = me.shipyard.position;
                        minDistance = gameMap.calculateDistance(ship.position, p);

                        // find neareast dropoff/shipyard
                        for (Dropoff dropoff: me.dropoffs.values()) {
                            int dis = gameMap.calculateDistance(ship.position, dropoff.position);
                            if (minDistance > dis) {
                                minDistance = dis;
                                p = dropoff.position;
                            }
                        }

                        if (gameMap.calculateDistance(ship.position, p) == 2) {
                        	List<Position> dropoffCardinals = p.getSurroundingCardinals();
	                    	int nr = 0;
	                    	for (Position pos: dropoffCardinals) {
	                    		pos = gameMap.normalize(pos);
	                    		if (gameMap.at(pos).isOccupied()) {
	                    			++nr;
	                    		}
	                    	}

	                    	if (nr == 3) {
	                    		commandQueue.add(ship.stayStill());
	                    		continue;
	                    	}
                        }

                        Direction dir = gameMap.naiveNavigate(ship, p);
                        commandQueue.add(ship.move(dir));

                        // make ship collect again
                        if (dir.equals(Direction.STILL)) {
                            shipState.put(ship.id.id, 'c');
                            shouldCollect.put(ship.id.id, 0);
                        }
                    }
                }
            }

            // spawn ships and shipyard
            if (me.ships.values().size() < nrShips &&
                    me.halite >= Constants.SHIP_COST &&
                    !gameMap.at(me.shipyard).isOccupied()) {
                commandQueue.add(me.shipyard.spawn());
            }

            game.endTurn(commandQueue);
        }
    }
}
