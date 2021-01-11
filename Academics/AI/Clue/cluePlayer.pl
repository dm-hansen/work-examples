%% Clue player
% Author: David M. Hansen
%
% Based on original clue player from 1999
%
%
% History:
%
%  2010  version adds logic to makeSuggestion and pick rules to avoid
%        making Telfer Dillow suggestions ourselves, if possible
%
%  2012  fixed bug when computing number of cards a player has (forgot to
%        account for 3 in solution). Fixed small bug where I had not
%        capitalized a variable in a rule. Also tried changing refutation to
%        withhold rooms but found that decreased win-ratio slightly; reverted
%        to choosing maximal. Added back in some sound and some aids 
%        to playing by hand that follow. 
%
%  2014  fixed setof/3 bugs throughout 2012; I misunderstood semantics
%        and had many setof/3 that had unbound variables so I was miscounting
%        things! Thanks to Steven Howell. Also added furthestfromme rule
%        for picking a room when all around known, as well as fixed bug
%        in end-game makesuggestion so I pick something that gets
%        refuted immediately. Also fixed cantHaveCard that left Player
%        potentially unbound allowing miscounting numbers who don't have
%        a card.
%


%%%%%%%%% Shortcuts for playing by hand %%%%%%%%%%%
% makeSuggestion shortcut
sug :- makeSuggestion(S,W,R,_), nl,nl,write(S),nl,write(W),nl,write(R),nl.
% makeAccusation shortcut
acc :- makeAccusation(_S,_W,_R,_).
% refute() shortcut
ref :- lastSuggestion(A, S, W, R), refute(S, W, R, A, C), write(C), nl.
% refutes() shortcut
refs(Refuter) :- suspect(Refuter), lastSuggestion(_,S,W,R), refutes(S,W,R,Refuter).
% cantRefute() shortcut
cantref(Agent) :- suspect(Agent), lastSuggestion(_,S,W,R), cantRefute(S,W,R,Agent).
% if we lost :-(
lost :- playSoundFor(lost).


% A rule to use instead of asserta to prevent reasserting the same fact
% and to see what new information we can derive
addNewFact(F) :- \+ F, asserta(F), addHasCardDeductions, addLikelyHasCardDeductions.
addNewFact(_).


% Turn logging on/off
%writeLog(_). % not logging, just succeed.
writeLog([]) :- nl.
writeLog([H|T]) :- write(H),write(' '),writeLog(T).


% Identify which rules I'll be asserting and retracting dynamically
:- dynamic suspect/1.
:- dynamic weapon/1.
:- dynamic room/1.
:- dynamic adjacent/2.
:- dynamic playerLocations/2.
:- dynamic lastSuggestion/4.
:- dynamic suggestionHistory/2.
:- dynamic suggested/4.
:- dynamic failedAccusation/4.
:- dynamic refuted/5.
:- dynamic likelyHasCardDeduction/2.
:- dynamic hasCardDeduction/2.
:- dynamic couldntRefute/4.
:- dynamic iShowed/2.
:- dynamic showedMe/2.
:- dynamic activePlayer/2.
:- dynamic activePlayers/1.
:- dynamic iHave/1.
:- dynamic iAm/1.
:- dynamic lastRoomMovedTo/1.
:- dynamic theSuspect/1.
:- dynamic theWeapon/1.
:- dynamic theRoom/1.
:- dynamic playedSound/1.
% Predicates that are not reset after each game
:- dynamic weaponPointsFor/2.
:- dynamic suspectPointsFor/2.
:- dynamic roomPointsFor/2.
:- dynamic gamesWon/2.
:- dynamic totalGames/1.


% This matching rule is used to clear all assertions from the database
% when initing the game.
reset :- retractall(suspect(_)),
         retractall(weapon(_)),
         retractall(room(_)),
         retractall(adjacent(_,_)),
         retractall(playerLocations(_,_)),
         retractall(lastSuggestion(_,_,_,_)),
         retractall(suggestionHistory(_,__)),
         retractall(suggested(_,_,_,_)),
         retractall(failedAccusation(_,_,_,_)),
         retractall(refuted(_,_,_,_,_)),
         retractall(likelyHasCardDeduction(_,_)),
         retractall(hasCardDeduction(_,_)),
         retractall(couldntRefute(_,_,_,_)),
         retractall(iShowed(_,_)),
         retractall(showedMe(_,_)),
         retractall(activePlayer(_,_)),
         retractall(activePlayers(_)),
         retractall(iHave(_)),
         retractall(iAm(_)),
         retractall(lastRoomMovedTo(_)),
         retractall(theSuspect(_)),
         retractall(theWeapon(_)),
         retractall(theRoom(_)),
         retractall(playedSound(_)).



%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% End of the game clauses
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% What a particular player knew - can't see any use for this. For now,
% give players points for how many things they knew
knew(Suspect, Weapon, Room, Player) :- updateSuspectScore(Suspect, Player),
                                       updateWeaponScore(Weapon, Player), 
                                       updateRoomScore(Room, Player).
% The items the player knew will be bound to a valid card, the unknown
% items will be invalid; so count the number of valid cards and
% ignore invalid cards - note that the first time through we'll
% assert that a player has 0 points
updateSuspectScore(Card, Player) :- 
   suspect(Card), 
   (suspectPointsFor(Player, Points), retract(suspectPointsFor(Player, _)); Points is 0),
   NewScore is Points+1, asserta(suspectPointsFor(Player, NewScore)).
% Else, if card is not valid, just succeed
updateSuspectScore(_Card, _Player). 

updateWeaponScore(Card, Player) :- 
   weapon(Card),
   (weaponPointsFor(Player, Points), retract(weaponPointsFor(Player, _)); Points is 0),
   NewScore is Points+1, asserta(weaponPointsFor(Player, NewScore)).
% Else, if card is not valid, just succeed
updateWeaponScore(_Card, _Player). 

updateRoomScore(Card, Player) :- 
   room(Card),
   (roomPointsFor(Player, Points), retract(roomPointsFor(Player, _)); Points is 0),
   NewScore is Points+1, asserta(roomPointsFor(Player, NewScore)).
% Else, if card is not valid, just succeed
updateRoomScore(_Card, _Player). 

% What the answer was and who won. Keep track of my wins and loses and
% total points and print out point totals after each game. 
answer(Suspect, Weapon, Room, WinningPlayer) :- 
   % This is merely for logging purposes to see if we were wrong about
   % an assumption of who had these cards
   checkAndRetract('Case File', Suspect),
   checkAndRetract('Case File', Weapon),
   checkAndRetract('Case File', Room),
   writeLog([WinningPlayer,'won, answer:',Suspect,Weapon,Room]),
   (gamesWon(WinningPlayer, Won), retract(gamesWon(WinningPlayer,_)); Won is 0), 
   NewScore is Won+1, asserta(gamesWon(WinningPlayer, NewScore)),
   (totalGames(Games),retract(totalGames(_)); Games is 0), 
   NewNumber is Games+1, asserta(totalGames(NewNumber)), 
   iAm(Me), writeLog(['\n\nI am',Me]),
   forall(activePlayer(P,_), printPoints(P)), nl,nl,nl.
printPoints(Player) :- 
   (suspectPointsFor(Player, Spoints); (\+ suspectPointsFor(Player, _), Spoints is 0)), 
   (weaponPointsFor(Player, Wpoints); (\+ weaponPointsFor(Player, _), Wpoints is 0)), 
   (roomPointsFor(Player, Rpoints); (\+ roomPointsFor(Player, _), Rpoints is 0)), 
   (gamesWon(Player, Won); (\+ gamesWon(Player, _), Won is 0)), 
      totalGames(Total), Points is Spoints+Wpoints+Rpoints,
   writeLog([Player,'\twon',Won,'of',Total,'games\tknown',Points,'cards,',
             Spoints,'suspects,',Wpoints,'weapons,',Rpoints,'rooms']).




%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Rules for determining what I know
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

allWeapons(AllWeapons) :- setof(W, weapon(W), AllWeapons). 
knownWeapons(KnownWeapons) :- findall(W, (weapon(W), hasCard(_, W)), KW),
                                list_to_set(KW, KnownWeapons).
possibleWeapons(Possible) :- allWeapons(All), knownWeapons(Known),
                           subtract(All, Known, Possible).
% Likely weapons 
unlikelyWeapons(Unlikely) :- findall(W, (weapon(W), likelyHasCard(_, W)), KW),
                                list_to_set(KW, Unlikely).
likelyWeapons(Likely) :- allWeapons(All), unlikelyWeapons(Unlikely),
                                    subtract(All, Unlikely, Likely).
% How to prove that I know THE weapon
% 1) If there is a card that no one can have - i.e., I can
%    prove that all active players do not have that card
isWeapon(Weapon) :- theWeapon(Weapon).
isWeapon(Weapon) :- weapon(Weapon), numCantHave(Weapon, NumPlayers),
                    allSuspects(AllPlayers), length(AllPlayers, NumPlayers),  
                    addNewFact(theWeapon(Weapon)), playSoundFor(weapon).
% 2) If there is only one card left in the set of possible objects 
isWeapon(Weapon) :- possibleWeapons([Weapon]), 
                    addNewFact(theWeapon(Weapon)), playSoundFor(weapon).

% Same thing, but based on likeliness
isLikelyWeapon(Weapon) :- weapon(Weapon), numLikelyCantHave(Weapon, NumPlayers),
                    allSuspects(AllPlayers), length(AllPlayers, NumPlayers).
% 2) If there is only one card left in the set of possible objects 
isLikelyWeapon(Weapon) :- likelyWeapons([Weapon]).

% 3) Answer true if we can prove it
isLikelyWeapon(Weapon) :- isWeapon(Weapon).


allSuspects(AllSuspects) :- setof(S, suspect(S), AllSuspects). 
knownSuspects(KnownSuspects) :- findall(S, (suspect(S), hasCard(_, S)), KS),
                                list_to_set(KS, KnownSuspects).
possibleSuspects(Possible) :- allSuspects(All), knownSuspects(Known),
                                    subtract(All, Known, Possible).
% Likely suspects
unlikelySuspects(Unlikely) :- findall(S, (suspect(S), likelyHasCard(_, S)), KS),
                                list_to_set(KS, Unlikely).
likelySuspects(Likely) :- allSuspects(All), unlikelySuspects(Unlikely),
                                    subtract(All, Unlikely, Likely).
% How to prove that I know THE suspect
% 1) If there is a card that no one can have - i.e., I can
%    prove that all active players do not have that card
isSuspect(Suspect) :- theSuspect(Suspect).
isSuspect(Suspect) :- suspect(Suspect), numCantHave(Suspect, NumPlayers),
                      allSuspects(AllPlayers), length(AllPlayers, NumPlayers), 
                      addNewFact(theSuspect(Suspect)), playSoundFor(suspect).
% 2) If there is only one card left in the set of possible objects 
isSuspect(Suspect) :- possibleSuspects([Suspect]), 
                      addNewFact(theSuspect(Suspect)), playSoundFor(suspect).

% Same thing, but based on likeliness 
isLikelySuspect(Suspect) :- suspect(Suspect), numLikelyCantHave(Suspect, NumPlayers),
                    allSuspects(AllPlayers), length(AllPlayers, NumPlayers).
% 2) If there is only one card left in the set of possible objects 
isLikelySuspect(Suspect) :- likelySuspects([Suspect]). 

% 3) Answer true if we can prove it
isLikelySuspect(Suspect) :- isSuspect(Suspect).

allRooms(AllRooms) :- setof(R, room(R), AllRooms). 
knownRooms(KnownRooms) :- findall(R, (room(R), hasCard(_, R)), KR),
                                list_to_set(KR, KnownRooms).
possibleRooms(Possible) :- allRooms(All), knownRooms(Known),
                           subtract(All, Known, Possible).
% Likely rooms 
unlikelyRooms(Unlikely) :- findall(R, (room(R), likelyHasCard(_, R)), KR),
                                list_to_set(KR, Unlikely).
likelyRooms(Likely) :- allRooms(All), unlikelyRooms(Unlikely),
                                    subtract(All, Unlikely, Likely).
% How to prove that I know THE room 
% 1) If there is a card that no one can have - i.e., I can
%    prove that all active players do not have that card
isRoom(Room) :- theRoom(Room).
isRoom(Room) :- room(Room), numCantHave(Room, NumPlayers),
                allSuspects(AllPlayers), length(AllPlayers, NumPlayers), 
                addNewFact(theRoom(Room)), playSoundFor(room).
% 2) If there is only one card left in the set of possible objects 
isRoom(Room) :- possibleRooms([Room]), addNewFact(theRoom(Room)), playSoundFor(room). 

% Same thing, but based on likeliness
isLikelyRoom(Room) :- room(Room), numLikelyCantHave(Room, NumPlayers),
                    allSuspects(AllPlayers), length(AllPlayers, NumPlayers).
% 2) If there is only one card left in the set of possible objects 
isLikelyRoom(Room) :- likelyRooms([Room]).

% 3) Answer true if we can prove it
isLikelyRoom(Room) :- isRoom(Room).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Ways to prove a player has a card
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% 1) I have the card
hasCard(Player, Card) :- iAm(Player), iHave(Card).

% 2) Another player has showed it to me
hasCard(Player, Card) :- showedMe(Player, Card).

% 3) Asserted dynamically
hasCard(Player, Card) :- hasCardDeduction(Player, Card).


% A recursive rule that tries to add new "has card" facts until we reach a fixed 
% point where there are no more facts to add. 
addHasCardDeductions :- refuted(Player, _, Suspect, Weapon, Room),
                   \+ hasCard(Player, Room), 
                   cantHave(Player, Suspect),
                   cantHave(Player, Weapon),
                   checkAndRetract(Player, Room),
                   addNewFact(hasCardDeduction(Player, Room)).

addHasCardDeductions :- refuted(Player, _, Suspect, Weapon, Room),
                   \+ hasCard(Player, Weapon), 
                   cantHave(Player, Suspect),
                   cantHave(Player, Room),
                   checkAndRetract(Player,Weapon),
                   addNewFact(hasCardDeduction(Player, Weapon)).

addHasCardDeductions :- refuted(Player, _, Suspect, Weapon, Room),
                   \+ hasCard(Player, Suspect), 
                   cantHave(Player, Weapon),
                   cantHave(Player, Room),
                   checkAndRetract(Player,Suspect),
                   addNewFact(hasCardDeduction(Player, Suspect)).

addHasCardDeductions.

% We need retract a likelyHasCard assumption, but before we do, see if
% it was valid or invalid
checkAndRetract(Player, Card) :- 
   likelyHasCardDeduction(Player, Card),
   writeLog(['c1 \t***** likelyHasCardDeduction(',Player,',',Card,') CONFIRMED!  *****']),
   % Retract as we'll replace it with a fact
   retractall(likelyHasCardDeduction(Player,Card)).

% Invalid deduction, remove ALL deductions, they'll be recomputed by the
% caller
checkAndRetract(Player, Card) :- 
   likelyHasCardDeduction(P, Card),
   writeLog(['c99 \t****** likelyHasCardDeduction(',P,',',Card,') RETRACTED! ******']),
   writeLog(['\t',Player,'had',Card]),
   % Retract ALL deductions since some may have been derived from this
   % faulty assumption and recompute any that are still valid
   retractall(likelyHasCardDeduction(_,_)).

% If we had no deduction about this card, continue on.
checkAndRetract(_, _). 


% A recursive rule that tries to add new "likely has card" facts until we reach 
% a fixed point where there are no more facts to add - difference from
% above is that we add suppositions here that may or may NOT be true.
addLikelyHasCardDeductions :- refuted(Player, _, Suspect, Weapon, Room),
                   \+ likelyHasCard(Player, Suspect),
                   \+ likelyCantHave(Player, Suspect),
                   likelyCantHave(Player, Weapon),
                   likelyCantHave(Player, Room),
                   addNewFact(likelyHasCardDeduction(Player, Suspect)),
writeLog(['d1 added',likelyHasCardDeduction(Player, Suspect)]).

addLikelyHasCardDeductions :- refuted(Player, _, Suspect, Weapon, Room),
                   \+ likelyHasCard(Player, Weapon),
                   \+ likelyCantHave(Player, Weapon),
                   likelyCantHave(Player, Suspect),
                   likelyCantHave(Player, Room),
                   addNewFact(likelyHasCardDeduction(Player, Weapon)),
writeLog(['d1 added',likelyHasCardDeduction(Player, Weapon)]).

addLikelyHasCardDeductions :- refuted(Player, _, Suspect, Weapon, Room),
                   \+ likelyHasCard(Player, Room), 
                   \+ likelyCantHave(Player, Room),
                   likelyCantHave(Player, Suspect),
                   likelyCantHave(Player, Weapon),
                   addNewFact(likelyHasCardDeduction(Player, Room)),
writeLog(['d1 added',likelyHasCardDeduction(Player, Room)]).

% These rules look at other suggestions this player has made and 
% tries to draw inferences about what is PROBABLY true.  
% If they previously made a suggestion that had 2-of-3 cards in 
% common with the current suggestion, then assume that the person 
% who refuted that earlier suggestion showed them the third card (as
% long as we can not prove they have one of the other cards).
% Note that we do not need to retract these hunches because they 
% are never used in finding a solution, only in picking cards 
% for suggestions. Even if we end up being wrong, it should 
% not hurt us.
addLikelyHasCardDeductions :- 
       suggested(Player, Suspect, Weapon, Room),
       refuted(Refuter, Player, DifferentSuspect, Weapon, Room),
       Suspect \== DifferentSuspect,
       \+ likelyHasCard(Refuter, DifferentSuspect),
       \+ likelyHasCard(Refuter, Weapon), 
       \+ likelyHasCard(Refuter, Room),
       %%\+ likelyCantHave(Refuter, DifferentSuspect),
       writeLog(['observed Telfer-Dillow!']),
       addNewFact(likelyHasCardDeduction(Refuter, DifferentSuspect)),
writeLog(['d2 added',likelyHasCardDeduction(Refuter, DifferentSuspect)]).

addLikelyHasCardDeductions :- 
       suggested(Player, Suspect, Weapon, Room),
       refuted(Refuter, Player, Suspect, DifferentWeapon, Room),
       Weapon \== DifferentWeapon,
       \+ likelyHasCard(Refuter, DifferentWeapon),
       \+ likelyHasCard(Refuter, Suspect), 
       \+ likelyHasCard(Refuter, Room),
       %%\+ likelyCantHave(Refuter, DifferentWeapon),
       writeLog(['observed Telfer-Dillow!']),
       addNewFact(likelyHasCardDeduction(Refuter, DifferentWeapon)),
writeLog(['d2 added',likelyHasCardDeduction(Refuter, DifferentWeapon)]).

addLikelyHasCardDeductions :- 
       suggested(Player, Suspect, Weapon, Room),
       refuted(Refuter, Player, Suspect, Weapon, DifferentRoom),
       Room \== DifferentRoom,
       \+ likelyHasCard(Refuter, DifferentRoom),
       \+ likelyHasCard(Refuter, Suspect), 
       \+ likelyHasCard(Refuter, Weapon),
       %%\+ likelyCantHave(Refuter, DifferentRoom),
       writeLog(['observed Telfer-Dillow!']),
       addNewFact(likelyHasCardDeduction(Refuter, DifferentRoom)),
writeLog(['d2 added',likelyHasCardDeduction(Refuter, DifferentRoom)]).

addLikelyHasCardDeductions.

% Another way would be if the player refutes a suggestion that we did not 
% know they could refute beforehand yet we knew there was only one unknown 
% card in their hand before the refutation - Hmmmmm, not so sure about
% this, seems like above rules capture that.



%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Ways to prove a person does not have a card - the key to finding solution!
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% 1) non-active players can not have any cards
cantHave(Player, _Card) :- suspect(Player), \+ activePlayer(Player,_).

% 2) If I am the player and do not have the card
cantHave(Player, Card) :- iAm(Player), \+ iHave(Card).

% 3) Can not have a card in a suggestion they could not refute
cantHave(Player, Suspect) :- couldntRefute(Player, Suspect, _, _).
cantHave(Player, Weapon) :- couldntRefute(Player, _, Weapon, _).
cantHave(Player, Room) :- couldntRefute(Player, _, _, Room).

% 4) Can not have a card that I can prove someone else has 
cantHave(Player, Card) :- suspect(Player), activePlayer(OtherPlayer, _), 
                          Player \== OtherPlayer,
                          hasCard(OtherPlayer, Card).

% 5) Can not have a card if I already know all the cards in their hand and this
%    card is not in their hand
cantHave(Player, Card) :- 
                activePlayer(Player, NumCards), \+ iAm(Player),
                setof(C, hasCard(Player, C), AllCards),
                \+ member(Card, AllCards),
                length(AllCards, NumCards).

% 6) Can not have a card that is known to be in the solution
cantHave(Player, Card) :- suspect(Player), theWeapon(Card).
cantHave(Player, Card) :- suspect(Player), theSuspect(Card).
cantHave(Player, Card) :- suspect(Player), theRoom(Card).

             
% Speculative can't have based on who likely does have cards. Obviously
% true if we've proven it
likelyCantHave(Player, Card) :- suspect(Player), cantHave(Player, Card).

% Can't have a card we speculate another person has
likelyCantHave(Player, Card) :- suspect(Player), activePlayer(OtherPlayer,_), 
                                Player \== OtherPlayer, 
                                likelyHasCard(OtherPlayer, Card).
% Assume that players don't have cards in any suggestion they make
% unless we think they may have the card
likelyCantHave(Player, Card) :- suspect(Player), \+ likelyHasCard(Player, Card),
                                 (suggested(Player, Card, _, _);
                                  suggested(Player, _, Card, _);
                                  suggested(Player, _, _, Card)).

% Assume players who made a bad accusation probably didn't have
% any cards in that accusation - though they might have made an
% error so we're not too sure
likelyCantHave(Player, Card) :- failedAccusation(Player, Card, _, _).
likelyCantHave(Player, Card) :- failedAccusation(Player, _, Card, _).
likelyCantHave(Player, Card) :- failedAccusation(Player, _, _, Card).




%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Number of players that cant have a particular card
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
numCantHave(Card, Number) :- findall(P, cantHave(P, Card), C),
                             list_to_set(C, Cant), length(Cant, Number).


numLikelyCantHave(Card, Number) :- findall(P, likelyCantHave(P, Card), C),
                                   list_to_set(C, Cant), length(Cant, Number).




%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%
% Database Update Rules - multiple rules insure card is legitimate
%
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%


% init removes all old assertaions from the knowledgebase and 
% asserts who I am, where I am, what cards I hold, as well as 
% all of the other information about the game. 
init(Me, StartRoom, Suspects, Weapons, RoomLists, Cards, ActivePlayers) :- 
   reset, 
   addNewFact(playedSound([])),
   playSoundFor(init),
   asserta(iAm(Me)),
   addMyCards(Cards),
   addSuspects(Suspects),
   addWeapons(Weapons),
   addRooms(RoomLists), 
   length(Suspects, NumS), length(Weapons, NumW), length(RoomLists, NumR),
   NumCards is NumS + NumW + NumR - 3, %three of these are in the solution!
   addPlayers(ActivePlayers, NumCards),
   % remember the list in order
   asserta(activePlayers(ActivePlayers)),
   % Put everyone in "nowhere" - Clunky, but works
   forall(suspect(S), asserta(playerLocations(S, []))),
   % Then place me in the actual room I'm starting in and set it up so I
   % can stay if I wisth
   moveTo(Me, StartRoom),
   asserta(lastRoomMovedTo(StartRoom)).

% Init a game with default configuration (could use this to play
% interactively)
initDefault(Me,StartRoom, MyCards, Suspects) :-
   %   Suspects = [missscarlet,mrgreen,mrswhite,professorplum,mrspeacock,colonelmustard],
   Weapons = [rope,leadpipe,wrench,candlestick,knife,revolver],
   Rooms = [[hall,[study,lounge]],
       [study,[hall,library,kitchen]],
       [library,[billiardroom,study]],
       [billiardroom,[library,conservatory]],
       [conservatory,[billiardroom,lounge,ballroom]],
       [ballroom,[conservatory,kitchen]],
       [kitchen,[diningroom,study,ballroom]],
       [diningroom,[kitchen,lounge]],
       [lounge,[diningroom,hall,conservatory]]],
   % Assume everyone is active
   init(Me, StartRoom, Suspects, Weapons, Rooms, MyCards, Suspects).

                
% Adds cards to the given set via assertion
addSuspects([]).
addSuspects([Card|T]) :- asserta(suspect(Card)), addSuspects(T).
addWeapons([]).
addWeapons([Card|T]) :- asserta(weapon(Card)), addWeapons(T).
addMyCards([]).
addMyCards([Card|T]) :- asserta(iHave(Card)), addMyCards(T).
% When we add players, we want to know how many cards each
% player has. We do that be figuring out the ceiling of the number
% of cards left to be distributed to the remaining players and
% then removing that number of cards from the recursive call. So,
% for example, if we initially have 10 cards for 3 players, the 
% first will have ceiling(NumCards/NumPlayers) (4) cards, and 
% the recursive call will be for 2 players with 6 cards remaining.
% We also initialize the turn history for each player so we can keep
% track of their suggestions in order to learn.
addPlayers([],_).
addPlayers([Player|T],NumCards) :- 
       asserta(suggestionHistory(Player,[])),
       length([Player|T], NumPlayers),
       PlayerCards is ceiling(NumCards/NumPlayers),
       asserta(activePlayer(Player, PlayerCards)), 
       RemainingCards is NumCards - PlayerCards,
       addPlayers(T, RemainingCards).

% Add rooms and set up the adjacency
addRooms([]).
addRooms([[Room|[RoomList]]|T]) :- asserta(room(Room)), 
                                   addPathFrom(Room, RoomList), 
                                   addRooms(T).

% Add path rule
addPathFrom(_, []).
addPathFrom(Room, [Adjacent|T]) :- asserta(adjacent(Room, Adjacent)), 
                                   addPathFrom(Room, T).



%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%
% Messages from the server to me
%
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

% Cards shown to me
showsMe(Player, Card) :- checkAndRetract(Player, Card),
                         addNewFact(showedMe(Player, Card)).


% Suggestions I have overheard note that I simply move
% players at this point rather than explicitly.
%    - maintenance of the lastSuggestion merely speeds data entry
suggests(Suspect, Weapon, Room, Player) :- 
        suspect(Suspect), weapon(Weapon), room(Room), suspect(Player), 
        moveTo(Player, Room), moveTo(Suspect, Room), 
        addNewFact(suggested(Player, Suspect, Weapon, Room)),
        retractall(lastSuggestion(_,_,_,_)),
        asserta(lastSuggestion(Player, Suspect, Weapon, Room)),
        suggestionHistory(Player,History),
        append(History, [[Suspect,Weapon,Room]], NewHistory),
        retractall(suggestionHistory(Player,History)),
        asserta(suggestionHistory(Player, NewHistory)).



% A player likely has a card if we asserted it above or can prove they 
% actually do have the card
likelyHasCard(Player, Card) :- suspect(Player), hasCard(Player, Card).
likelyHasCard(Player, Card) :- suspect(Player), likelyHasCardDeduction(Player, Card).


                        

% Refute; first make sure I can refute, then choose a card to use
refute(Suspect, Weapon, Room, Agent, Card) :-
   (iHave(Suspect); iHave(Weapon); iHave(Room)), 
   refuteWith(Suspect, Weapon, Room, Agent, Card),
   addNewFact(iShowed(Agent, Card)).


% Ways I could disprove a suggestion - the following attempt to show
% most common things first.

% 1) If I already showed them a card, show it again 
refuteWith(Suspect, Weapon, Room, Agent, Card) :-
        iShowed(Agent, Card),
        (Card=Suspect; Card=Weapon; Card=Room),
        writeLog(['x1 Refuting',Agent,'with', Card,'again']).

% 2) Show a card I've shown that is proportionally rare, based on numbers, 
%    not what I have
refuteWith(Suspect, Weapon, Room, Agent, Card) :-
            % Compute the most of each type
            iShowed(_Someone, Card),
            allWeapons(Weapons), length(Weapons, NW),
            allSuspects(Suspects), length(Suspects, NS),
            allRooms(Rooms), length(Rooms, NR),
            maximizeRefute(Suspect, NS, Weapon, NW, Room, NR, Card),
            writeLog(['x2 Refuting',Agent,'with maximal choice among shown', Card]).

% 3) Show a card that is proportionally rare, based on numbers, not what
%    I have
refuteWith(Suspect, Weapon, Room, Agent, Card) :-
            % Compute the most of each type
            allWeapons(Weapons), length(Weapons, NW),
            allSuspects(Suspects), length(Suspects, NS),
            allRooms(Rooms), length(Rooms, NR),
            maximizeRefute(Suspect, NS, Weapon, NW, Room, NR, Card),
            writeLog(['x3 Refuting',Agent,'with maximal choice', Card]).

% Show a card that there are the most of. In case of ties we prefer
% suspects and weapons to rooms since rooms are constrained
maximizeRefute(Suspect, NS, Weapon, NW, Room, NR, Suspect) :- 
      % I have the suspect
      iHave(Suspect), 
      % There are more suspects than anything else or I don't have the
      % weapon or room
      ((NS >= NW, NS>=NR); \+(iHave(Weapon); iHave(Room))).
maximizeRefute(_Suspect, _NS, Weapon, NW, Room, NR, Weapon) :- 
      iHave(Weapon), 
      (NW >= NR; \+iHave(Room)).
maximizeRefute(_Suspect, _NS, _Weapon, _NW, Room, _NR, Room) :- 
      iHave(Room).





% Refutations I have overheard
refutes(Suspect, Weapon, Room, Refuter) :- 
         lastSuggestion(Suggestor, Suspect,  Weapon, Room),
         addNewFact(refuted(Refuter, Suggestor, Suspect, Weapon, Room)).





% Failed refutations I have overheard
cantRefute(Suspect, Weapon, Room, Agent) :- 
          addNewFact(couldntRefute(Agent, Suspect, Weapon, Room)).


% Failed accusations I have overheard - obviously assume that the accusing 
% player did not have any of the cards in their accusation if I don't know 
% any better
accuse(Suspect, Weapon, Room, Agent) :-
          addNewFact(failedAccusation(Agent, Suspect, Weapon, Room)).



% Player moves I have observed
moveTo(Player, Room) :- retractall(playerLocations(Player, OldList)), 
                        asserta(playerLocations(Player, [Room|OldList])).

% Where player is now
isIn(Player, Room) :- playerLocations(Player, [Room | _History]).




%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% 
% make Accusation if I know the three items!
%
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
makeAccusation(Suspect, Weapon, Room, Me) :- 
  iAm(Me), 
  isRoom(Room),
  isSuspect(Suspect), 
  isWeapon(Weapon),
  writeLog(['\n\n\n**********\tI accuse',Suspect,Weapon,Room]), 
  playSoundFor(accuse).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%
% Take a turn - always make a suggestion - try to avoid 
% "Telfer Dillow" if possible, which is asking for 2-of-3 we've
% suggested before as that gives help to other players. Also, if
% I know the solution and this suggestion is superfluous, pick 
% things that others have shown me so as
% not to give any additional information at the end
%
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
makeSuggestion(Suspect, Weapon, Room, Me) :- 
   makeSuggestion(Suspect, Weapon, Room, Me, _).
makeSuggestion(Suspect, Weapon, Room, Me, _) :- 
          isRoom(_R), isSuspect(_S), isWeapon(_W),
          % Pick a known suspect, weapon, or room that the player next 
          % to me in turn order has just to get this over with 
          % immediately so that we don't disclose anything more to any
          % other players about the solution
          iAm(Me), 
          nextPlayer(NextPlayer), hasCard(NextPlayer, C), 
          ( 
             % Has a weapon? Pick any suspect and any room
             (weapon(C), Weapon = C, suspect(Suspect), 
                canMoveTo(Room));
             % Has a suspect? Pick any weapon and any room
             (suspect(C), Suspect = C, weapon(Weapon),
                canMoveTo(Room));
             % Has a room? Pick any weapon and any suspect
             (room(C), canMoveTo(C), Room = C,
                weapon(Weapon), suspect(Suspect))
          ),
          % Record our suggestion and remember the room for room moves later
          retractall(lastRoomMovedTo(_)),
          asserta(lastRoomMovedTo(Room)),
          suggests(Suspect, Weapon, Room, Me),
          writeLog(['   *** Solution Known!!!! ***\nMake picks from what next player has to get it over with']),
          writeLog(['m1 I (bogusly) suggest',Suspect,Weapon,Room]).

% Same thing, but pick from random as my neighbor has nothing that I know of
makeSuggestion(Suspect, Weapon, Room, Me,_) :- 
          isRoom(_R), isSuspect(_S), isWeapon(_W),
          % Pick a known suspect and weapon just to get this over with
          knownSuspects(KS), randomElement(KS, Suspect),
          knownWeapons(KW), randomElement(KW, Weapon),
          pickRoom(Suspect, Weapon, Room), 
          iAm(Me), 
          % Record our suggestion and remember the room for room moves later
          retractall(lastRoomMovedTo(_)),
          asserta(lastRoomMovedTo(Room)),
          suggests(Suspect, Weapon, Room, Me),
          writeLog(['    *** Solution Known!!!! ***\nMake picks from known to get it over with']),
          writeLog(['m2 I (bogusly) suggest',Suspect,Weapon,Room]).

% Very special case where I know the room and the suspect and either the
% suspect OR someone I have in my hand is near the room and I can move
% to a room that I have, but that is not adjacent to the room, then drag
% that suspect to the room with me and we'll work on the weapon.
makeSuggestion(Suspect, Weapon, Room, Me,_) :-
          % I know the room and the suspect
          isRoom(TheRoom), isSuspect(TheSuspect),
          % The suspect, or an active player I have in my hand,
          % is in, or can can move to the room - so we're going to pick them
          ((activePlayer(Suspect,_), iHave(Suspect)) ; Suspect=TheSuspect),
          isIn(Suspect, Aroom), (Aroom=TheRoom; adjacent(Aroom, TheRoom)),
          % I cam move to a room that I have, but that is not adjacent
          % to The room
          canMoveTo(Room), iHave(Room), \+adjacent(Room, TheRoom),
          % Now pick a weapon
          pickWeapon(Suspect, Weapon, Room),
          % Record our suggestion and note that we can't stay here
          iAm(Me),
          % Record our suggestion and remember the room for room moves later
          retractall(lastRoomMovedTo(_)),
          asserta(lastRoomMovedTo(Room)),
          suggests(Suspect, Weapon, Room, Me),
          writeLog(['    *** Solution Known!!!! ***\nDrag a suspect away from the room']),
          writeLog(['m3 I drag',Suspect,'with me and suggest',Suspect,Weapon,Room])     .

% Don't know the solution, so use smart rules to pick - always pick the
% room first since it's constrained by geography, then pick from the set
% of things I know the most about (i.e., have fewest choices remaiing) 
% followed by the least constrained set
makeSuggestion(Suspect, Weapon, Room, Me,_) :- 
          iAm(Me), 
          pickRoom(Suspect, Weapon, Room), 
          % Get number of players who can't have some suspect S
          possibleSuspects(PossibleSus),
          setof(NS, S^(suspect(S), member(S, PossibleSus), numCantHave(S, NS)), NumSs),
          last(NumSs, MaxNumSus),
          % Now get the list of all suspects that have MaxNumSus as the
          % number of people who don't have them
          setof(S2, (suspect(S2), member(S2, PossibleSus), numCantHave(S2, MaxNumSus)), Suspects),
          % Get number of players who can't have some weapon W
          possibleWeapons(PossibleWeap),
          setof(NW, W^(weapon(W), member(W, PossibleWeap), numCantHave(W, NW)), NumWs),
          last(NumWs, MaxNumWeap),
          % Now get the list of all weapons that have MaxNumWeap as the
          % number of people who don't have them
          setof(W2, (weapon(W2), member(W2, PossibleWeap), numCantHave(W2, MaxNumWeap)), Weapons),
          % If we know more about suspects than weapons, then select
          % those first, otherwise select the weapon first
          length(Suspects, NumSuspects), length(Weapons, NumWeapons),
          (
            % If we have fewer unknown suspects then pick that first
            (NumSuspects < NumWeapons, 
               pickSuspect(Suspect, Weapon, Room),
               pickWeapon(Suspect, Weapon, Room));
            % Otherwise pick the weapon first
            (pickWeapon(Suspect, Weapon, Room), 
                 pickSuspect(Suspect, Weapon, Room))
          ),
          % Record our suggestion and remember the room for room moves later
          retractall(lastRoomMovedTo(_)),
          asserta(lastRoomMovedTo(Room)),
          suggests(Suspect, Weapon, Room, Me),
          writeLog(['m4 I suggest',Suspect,Weapon,Room]).


%%%%
% Telfer-Dillow is a suggestion (ground) that uses at least two of three
% previous elements of a previous suggestion that I made
%%%%
isTelferDillow(Suspect, Weapon, Room) :-
   iAm(Me), ground(Suspect), ground(Weapon), ground(Room),
      (suggested(Me, _, Weapon, Room);
       suggested(Me, Suspect, _, Room);
       suggested(Me, Suspect, Weapon, _)),
   writeLog(['t1 Avoiding Telfer-Dillow suggestion']).


% Helper predicate to tell me who is next to me in turn order
nextPlayer(NextPlayer) :- 
   iAm(Me), 
   activePlayers(Players), length(Players, NumActive),
   nth0(MyIndex, Players, Me), NextIndex is mod(MyIndex+1, NumActive), 
   nth0(NextIndex, Players, NextPlayer).

% Helper rule for picking rooms that allows us to stay in a room if we were 
% dragged into the room
canMoveTo(NewRoom) :- iAm(Me), isIn(Me, NewRoom), 
                      \+ lastRoomMovedTo(NewRoom). 
% Second form if we can't stay
canMoveTo(NewRoom) :- iAm(Me), isIn(Me, Room), adjacent(Room, NewRoom).



%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Room selection rules - do not really use Suspect or Weapon 
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% 1) If I know the room, use one of the pickRoom/4 rules
pickRoom(Suspect, Weapon, NewRoom) :- isRoom(TheRoom),
                   writeLog(['   *** Room is the', TheRoom, '***']),
                   pickRoom(Suspect, Weapon, NewRoom, TheRoom).

% If we THINK we know the room and can move there, do so.
pickRoom(_Suspect, _Weapon, NewRoom) :- isLikelyRoom(NewRoom), canMoveTo(NewRoom),
                   writeLog(['r1 *** Room is very likely the', NewRoom, 'Move there!']). 

% Same as above, but pick an adjacent room that is on the
% shortest path TO the likely room - pick one that is likely not in anyone's hand
pickRoom(_Suspect, _Weapon, NewRoom) :- isLikelyRoom(TheRoom),
                   iAm(Me), isIn(Me, Room), 
                   nextRoom(Room, TheRoom, NewRoom),
                   \+ likelyHasCard(_, NewRoom),
                   writeLog(['r2 *** Room is very likely the', TheRoom, '***\nI am in',Room, 
                              'move through possible',NewRoom]).

% Same as above, but pick a room that is known to be held by the person
% furthest from me in the list so I don't get refuted right away
pickRoom(_Suspect, _Weapon, NewRoom) :- isLikelyRoom(TheRoom),
                   iAm(Me), isIn(Me, Room), Room \== TheRoom,
                   % Get the rooms on the way to The Room
                   setof(R, nextRoom(Room, TheRoom, R), NextRooms),
                   % Get the number of players playing
                   activePlayers(Players), length(Players, NumActive),
                   NumOtherPlayers is NumActive-1,
                   % Pick a room that is held by person furthest from me
                   furthestPlayerFromMeCantDisprove(Players, NextRooms, NewRoom, NumOtherPlayers),
                   writeLog(['r3 *** Room is very likely the', TheRoom, '***\nI am in',Room, 
                             'move through room someone has']).

% Same as above, but I'm in the likely room and can't stay. So pick a room that is 
% adjacent that is held by the person furthest from me in the list so I don't get refuted 
% right away
pickRoom(_Suspect, _Weapon, NewRoom) :- isLikelyRoom(TheRoom),
                   iAm(Me), isIn(Me, TheRoom), 
                   setof(R, canMoveTo(R), NextRooms),
                   % Get the number of players playing
                   activePlayers(Players), length(Players, NumActive),
                   NumOtherPlayers is NumActive-1,
                   % Pick a room that is held by person furthest from me
                   furthestPlayerFromMeCantDisprove(Players, NextRooms, NewRoom, NumOtherPlayers),
                   writeLog(['r4 *** Room is very likely the room I am in -', TheRoom,
                             '***\nMove to an adjacent room']).

% Same as above, but pick any room nextdoor; this should never happen,
% but it does.
% TODO figure out why this rule is firing!
pickRoom(_Suspect, _Weapon, NewRoom) :- isLikelyRoom(TheRoom),
                   canMoveTo(NewRoom),
                   iAm(Me), isIn(Me, R),
                   writeLog(['r999 *** Room is very likely the', TheRoom, 'I am in',R,
                   'picking adjacent - this is bad!']).

% 2) I do not know where it occured

%    a) Pick a room I do not know that is adjacent to me and is unlikely
%       to be in someone's hand and go there
pickRoom(_Suspect, _Weapon, NewRoom) :- 
       % Get number of players who don't have some room R
       likelyRooms(Likely),
       setof(N, R^(canMoveTo(R), member(R, Likely), numLikelyCantHave(R, N)), NumRs),
       last(NumRs, MaxNum),
       % Now get the list of all rooms that have MaxNum as the
       % number of people who can't have them
       setof(R2, (canMoveTo(R2), member(R2, Likely), numLikelyCantHave(R2, MaxNum)),
               PickFrom),
       % Simiarly, of the adjacent rooms, find the one with the most unknown
       % adjacent rooms as the tie-breaker and move there.
       setof(N2, R3^(member(R3, PickFrom), withUnknownAdjacent(R3, N2)), NumARs),
       last(NumARs, MaxNumAR),
       setof(Rm, (member(Rm, PickFrom), withUnknownAdjacent(Rm, MaxNumAR)), BestRooms),
       randomElement(BestRooms, NewRoom),
       writeLog(['r5 room unknown, pick from',
              PickFrom, 'I believe', MaxNum, 'people cant have with', MaxNumAR, 'adjacent']).


%    b) Pick any room I can move to that's unknown (could be room I'm in)
pickRoom(_Suspect, _Weapon, NewRoom) :- 
         possibleRooms(Possible),
         setof(R, (canMoveTo(R), member(R, Possible)), PickFrom),
         % Pick a random element from that list 
         randomElement(PickFrom, NewRoom),
         writeLog(['r6 room unknown, pick from',PickFrom,'that are unknown']).

%    c) If we don't know the suspect and weapon, then pick a room I have in 
%       my hand and have not shown and go there 
pickRoom(_Suspect, _Weapon, NewRoom) :- \+ isSuspect(_S), \+ isWeapon(_W),
               setof(N, P^R^(canMoveTo(R), iHave(R), 
                           \+ iShowed(P, R), 
                           withUnknownAdjacent(R, N)), NumARs),
               last(NumARs, MaxNumAR),
               setof(Rm, P2^(canMoveTo(Rm), iHave(Rm), 
                           \+ iShowed(P2, Rm), 
                           withUnknownAdjacent(Rm, MaxNumAR)), BestRooms),
               randomElement(BestRooms, NewRoom),
               writeLog(['r7 room unknown, pick adjacent I have and have not shown']).

%    d) If we don't know the suspect and weapon, then pick a room I have in 
%       my hand and go there 
pickRoom(_Suspect, _Weapon, NewRoom) :- \+ isSuspect(_S), \+ isWeapon(_W),
               setof(N, R^(canMoveTo(R), iHave(R), 
                           withUnknownAdjacent(R, N)), NumARs),
               last(NumARs, MaxNumAR),
               setof(Rm, (canMoveTo(Rm), iHave(Rm), 
                           withUnknownAdjacent(Rm, MaxNumAR)), BestRooms),
               randomElement(BestRooms, NewRoom),
               writeLog(['r8 room unknown, pick adjacent I have']).

%    e) Pick a likely room on the way to a close possible room that is held
%    by the person furthest from me
pickRoom(_Suspect, _Weapon, NewRoom) :- iAm(Me), isIn(Me, Room), 
         % Find the distances to possible rooms, exclude the current room I'm in
         likelyRooms(Likely),
         setof(D, R^(room(R), R\==Room, member(R, Likely), distance(Room, R, D)), 
                 [Dist | _]),
         % Now find all unknown destinations that far away 
         setof(Rm, (room(Rm), member(Rm, Likely), distance(Room, Rm, Dist)), Close),
         % Pick one that someone far from me has in their hand
         % Get the number of players playing
         activePlayers(Players), length(Players, NumActive),
         NumOtherPlayers is NumActive-1,
         % Pick a room that is held by person furthest from me
         furthestPlayerFromMeCantDisprove(Players, Close, Destination, NumOtherPlayers),
         % Pick the next room on the shortest path to that destination
         nextRoom(Room, Destination, NewRoom),
         writeLog(['r9 room unknown, all known around, pick shortest path to possible room']).

%    f) Same as above, but only use truly has card.
pickRoom(_Suspect, _Weapon, NewRoom) :- iAm(Me), isIn(Me, Room), 
         % Find the distances to possible rooms, exclude the current room I'm in; remember the 
         % shortest distance which is at the head of the list
         possibleRooms(Possible),
         setof(D, R^(room(R), R\==Room, member(R, Possible), distance(Room, R, D)), 
                 [Dist | _]),
         % Now find all unknown destinations that far away 
         setof(Rm, (room(Rm), member(Rm, Possible), distance(Room, Rm, Dist)), Close),
         % Pick one that someone far from me has in their hand
         % Get the number of players playing
         activePlayers(Players), length(Players, NumActive),
         NumOtherPlayers is NumActive-1,
         % Pick a room that is held by person furthest from me
         furthestPlayerFromMeCantDisprove(Players, Close, Destination, NumOtherPlayers),
         % Pick the next room on the shortest path to that destination
         nextRoom(Room, Destination, NewRoom),
         writeLog(['r10 room unknown, all known around, pick shortest path to possible room']).

%    g) Pick an adjacent room with the most unknown rooms adjacent and go there
pickRoom(_Suspect, _Weapon, NewRoom) :- 
       % Get all rooms we can move to
       setof(R, canMoveTo(R), PickFrom),
       % Find the one with the most unknown adjacent rooms and move there.
       setof(N, R2^(member(R2, PickFrom), withUnknownAdjacent(R2, N)), NumARs),
       last(NumARs, MaxNumAR),
       setof(Rm, (member(Rm, PickFrom), withUnknownAdjacent(Rm, MaxNumAR)), BestRooms),
       randomElement(BestRooms, NewRoom),
       writeLog(['r11 room and all around known, pick from', PickFrom, 'with', MaxNumAR, 'adjacent']).

%    f) Pick a random adjacent room
pickRoom(_Suspect, _Weapon, NewRoom) :- 
         bagof(R, canMoveTo(R), PickFrom),
         % Pick a random element from that list 
         randomElement(PickFrom, NewRoom),
         writeLog(['r99 room unknown, last ditch picking random from',PickFrom]).



% PickRoom/4 rules to use when I know the room
%   a) If I know the solution (i.e., can prove who the suspect and
%   weapon are), then assume this is a throwaway suggestion and try to move 
%   to a room that is KNOWN so that noone is helped; probably redundant
%   as the make suggestion rule will pick a suspect or weapon that the
%   person next to me has so we get disproved immediately.
pickRoom(_Suspect, _Weapon, NewRoom, _TheRoom) :- 
   isSuspect(_S), isWeapon(_W), knownRooms(RS), member(NewRoom, RS),
               canMoveTo(NewRoom), writeLog(['r90 go to room that is known']).
%   b) If I have an adjacent room and have shown the room to someone, go 
%      there to throw them off 
pickRoom(_Suspect, _Weapon, NewRoom, _TheRoom) :- canMoveTo(NewRoom), 
               iHave(NewRoom), iShowed(_, NewRoom),
               writeLog(['r91 go to room I have and have shown']).
%   c) If I have an adjacent room go there 
pickRoom(_Suspect, _Weapon, NewRoom, _TheRoom) :- canMoveTo(NewRoom), iHave(NewRoom), 
               writeLog(['r92 go to room I have']).
%   d) Otherwise head to THE room if I am adjacent to it
pickRoom(_Suspect, _Weapon, TheRoom , TheRoom) :- canMoveTo(TheRoom),
               writeLog(['r93 go to THE room']).

%   e) Otherwise head to any room that is in my hand or THE room;
%      best to pick one on shortest path that is held by a person far
%      from me in turn order so I can learn the most before getting
%      disproved.
pickRoom(_Suspect, _Weapon, NewRoom, TheRoom) :- iAm(Me), isIn(Me, Room), 
        % Find the distances to rooms I have or THE room (excluding the one I'm in); remember
        % the shortest distance at the head of the list
        setof(D, R^(room(R), R\==Room, (iHave(R); R=TheRoom), distance(Room, R, D)), [Dist | _]),
        % Now find destinations that are that far away and go to the next room
        setof(Rm, (room(Rm), (iHave(Rm); Rm = TheRoom), distance(Room, Rm, Dist)), Close),
        % Pick one that someone far from me has in their hand
        % Get the number of players playing
        activePlayers(Players), length(Players, NumActive),
        NumOtherPlayers is NumActive-1, 
        % Pick a room that is held by person furthest from me
        furthestPlayerFromMeCantDisprove(Players, Close, Destination, NumOtherPlayers),
        % Pick the next room on the shortest path to that destination
        nextRoom(Room, Destination, NewRoom),
        writeLog(['r94 all known around, pick on short path to The Room, or one I have']).

%   f) Special case where I'm in THE room and have no rooms in my hand!
%      In that case, pick adjacent that is held by a person far
%      from me in turn order so I can learn the most before getting
%      disproved.
pickRoom(_Suspect, _Weapon, NewRoom, TheRoom) :- iAm(Me), isIn(Me, TheRoom), 
        % Find adjacent
        setof(R, canMoveTo(R), Close),
        % Pick one that someone far from me has in their hand
        % Get the number of players playing
        activePlayers(Players), length(Players, NumActive),
        NumOtherPlayers is NumActive-1, 
        % Pick a room that is held by person furthest from me
        furthestPlayerFromMeCantDisprove(Players, Close, NewRoom, NumOtherPlayers),
        writeLog(['r95 all known around and I am in THE room, pick adjacent']).

% Same as above, but pick any room nextdoor; this should never happen
% really, but it does (VERY rarely).
pickRoom(_Suspect, _Weapon, NewRoom, TheRoom) :-
                   iAm(Me), isIn(Me, R),
                   canMoveTo(NewRoom),
                   writeLog(['r9999 *** Room is the', TheRoom, 'I am in',R,
                   'picking adjacent - this is bad!']).


% Helper predicate to tell me how many likely unknown rooms are adjacent to a 
% particluar room
withUnknownAdjacent(Room, Number) :- 
   findall(R, (adjacent(Room, R), likelyCantHave(_, R)), Unk),
               list_to_set(Unk, Unknown), length(Unknown, Number).





%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Weapon selection rules - do not really use Suspect or Room, but might
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% If I know the weapon, use one of the pickWeapon/4 rules
pickWeapon(Suspect, Weapon, Room) :- isWeapon(TheWeapon),
                   writeLog(['   *** Weapon is the', TheWeapon, '***']), 
                   pickWeapon(Suspect, Weapon, Room, TheWeapon).

% If we THINK we know the weapon, ask for it
pickWeapon(_Suspect, Weapon, _Room) :- isLikelyWeapon(Weapon),
                   writeLog(['w1 *** Weapon is very likely the', Weapon, '***']).

% 2) If I do not know the weapon


% a) If I do not know the weapon pick the unknown weapon that the largest 
%     number of players dont have and try that 
pickWeapon(Suspect, Weapon, Room) :- 
   % Get number of players who can't have some weapon W
   likelyWeapons(Likely),
   setof(N, W^(weapon(W), member(W, Likely), numLikelyCantHave(W, N)), Nums),
   last(Nums, MaxNum),
   % Get the list of all weapons that have MaxNum as the
   % number of people who can't have them
   setof(W2, (weapon(W2), member(W2, Likely), numLikelyCantHave(W2, MaxNum)),
         PickFrom),
   % Pick a random element from that list as long as it's
   % got elements in it!
   randomElement(PickFrom, Weapon),
   \+ isTelferDillow(Suspect, Weapon, Room),
   writeLog(['w2 weapon unknown, pick random weapon from',
             PickFrom, 'I believe', MaxNum, 'people cant have']).

% Same as above, don't worry about Telfer-Dillow
pickWeapon(_Suspect, Weapon, _Room) :- 
   % Get number of players who can't have some weapon W
   likelyWeapons(Likely),
   setof(N, W^(weapon(W), member(W, Likely), numLikelyCantHave(W, N)), Nums),
   last(Nums, MaxNum),
   % Get the list of all weapons that have MaxNum as the
   % number of people who can't have them
   setof(W2, (weapon(W2), member(W2, Likely), numLikelyCantHave(W2, MaxNum)),
         PickFrom),
   % Pick a random element from that list as long as it's
   % got elements in it!
   randomElement(PickFrom, Weapon),
   writeLog(['w3 weapon unknown, pick random weapon from',
             PickFrom, 'I believe', MaxNum, 'people cant have']).

% b) Same as a, except we only use what we actually know to be true
pickWeapon(Suspect, Weapon, Room) :- 
   % Get number of players who don't have some weapon W
   possibleWeapons(Possible),
   setof(N, W^(weapon(W), member(W, Possible), numCantHave(W, N)), Nums),
   last(Nums, MaxNum),
   % Now get the list of all weapons that have MaxNum as the
   % number of people who don't have them
   setof(W2, (weapon(W2), member(W2, Possible), numCantHave(W2, MaxNum)),
           PickFrom),
   % Pick a random element from that list as long as it's
   % got elements in it!
   randomElement(PickFrom, Weapon),
   \+ isTelferDillow(Suspect, Weapon, Room),
   writeLog(['w4 weapon unknown, pick random weapon from',
            PickFrom, 'I know', MaxNum, 'people cant have']).

% Same above, don't worry about Telfer-Dillow
pickWeapon(_Suspect, Weapon, _Room) :- 
   % Get number of players who don't have some weapon W
   possibleWeapons(Possible),
   setof(N, W^(weapon(W), member(W, Possible), numCantHave(W, N)), Nums),
   last(Nums, MaxNum),
   % Now get the list of all weapons that have MaxNum as the
   % number of people who don't have them
   setof(W2, (weapon(W2), member(W2, Possible), numCantHave(W2, MaxNum)),
           PickFrom),
   % Pick a random element from that list as long as it's
   % got elements in it!
   randomElement(PickFrom, Weapon),
   writeLog(['w5 weapon unknown, pick random weapon from',
            PickFrom, 'I know', MaxNum, 'people cant have']).


% c) Probably some bad inferences, so choose from all unknown
pickWeapon(_Suspect, Weapon, _Room) :- 
   possibleWeapons(Possible), 
   randomElement(Possible, Weapon),
   writeLog(['w99 weapon unknown, pick random']).


% PickWeapon/4 rules to use when I know the weapon
%    a) pick a weapon that I have but have not shown but have suggested 
pickWeapon(_Suspect, Weapon, _Room, _TheWeapon) :-  iAm(Me), 
   setof(W, P^S^R^(weapon(W), iHave(W), \+iShowed(P, W),
             suggested(Me, S, W, R)), NotShown),
   randomElement(NotShown, Weapon),
   writeLog(['w90 pick weapon I have and have not shown but have suggested']).


%    b) pick a weapon that I have and suggested
pickWeapon(_Suspect, Weapon, _Room, _TheWeapon) :- iAm(Me), 
   setof(W, S^R^(weapon(W), iHave(W), suggested(Me, S, W, R)), Suggested),
   randomElement(Suggested, Weapon), 
   writeLog(['w91 pick weapon I have and have suggested']).

%    c) pick a weapon that I have but have not shown
pickWeapon(_Suspect, Weapon, _Room, _TheWeapon) :-  
   setof(W, P^(weapon(W), iHave(W), \+iShowed(P, W)), NotShown),
   randomElement(NotShown, Weapon),
   writeLog(['w92 pick weapon I have and have not shown']).

%    d) pick a weapon that I have
pickWeapon(_Suspect, Weapon, _Room, _TheWeapon) :- 
   setof(W, (weapon(W), iHave(W)), NotShown),
   randomElement(NotShown, Weapon), 
   writeLog(['w93 pick weapon I have']).

%    e) pick the weapon itself - no point picking something someone will show 
%       me again since I need to find out other information
pickWeapon(_Suspect, Weapon, _Room, Weapon) :- 
   writeLog(['w94 I have none in my hand so pick THE weapon']).



% Helper predicate to tell me how many different players, other than myself,
% have refuted a suggestion with a particular unknown weapon 
%%%%%%%%%%%%%%%%  Unused right now
weaponRefuted(Weapon, Number) :- 
   findall(R, (refuted(R, P, _, Weapon, _), 
               \+ iAm(P), \+ iAm(R), \+ hasCard(_, Weapon)), 
          Ref),
          list_to_set(Ref, Refuted), length(Refuted, Number).
                



%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Suspect selection rules - do not really use Weapon or Room, but might
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% If I know the suspect, use one of the pickSuspect/4 rules
pickSuspect(Suspect, Weapon, Room) :- isSuspect(TheSuspect),
   writeLog(['   *** Murderer is', TheSuspect, '***']),
   pickSuspect(Suspect, Weapon, Room, TheSuspect).

% If we THINK we know the suspect pick it
pickSuspect(Suspect, _Weapon, _Room) :- isLikelySuspect(Suspect),
   writeLog(['s1 *** Murderer is very likely', Suspect, '***']).

% 2) If I do not know the suspect 

% a) pick the unknown suspect that the largest number of players don't
%     have and try that.
pickSuspect(Suspect, Weapon, Room) :- 
   % Get number of players who don't have some suspect S
   likelySuspects(Likely),
   setof(N, S^(suspect(S), member(S, Likely), numLikelyCantHave(S, N)), Nums),
   last(Nums, MaxNum),
   % Get the list of all weapons that have MaxNum as the
   % number of people who can't have them
   setof(S2, (suspect(S2), member(S2, Likely), numLikelyCantHave(S2, MaxNum)),
         PickFrom),
   % Pick a random element from that list as long as it's
   % got elements in it!
   randomElement(PickFrom, Suspect),
   \+ isTelferDillow(Suspect, Weapon, Room),
   writeLog(['s2 suspect unknown, pick random suspect from',
              PickFrom, 'I believe', MaxNum, 'people cant have']).

% Same as above, ignore Telfer-Dillow
pickSuspect(Suspect, _Weapon, _Room) :- 
   % Get number of players who don't have some suspect S
   likelySuspects(Likely),
   setof(N, S^(suspect(S), member(S, Likely), numLikelyCantHave(S, N)), Nums),
   last(Nums, MaxNum),
   % Now get the list of all supects that have MaxNum as the
   % number of people who can't have them
   setof(S2, (suspect(S2), member(S2, Likely), numLikelyCantHave(S2, MaxNum)),
           PickFrom),
   % Pick a random element from that list as long as it's
   % got elements in it!
   randomElement(PickFrom, Suspect),
   writeLog(['s3 suspect unknown, pick random suspect from',
              PickFrom, 'I believe', MaxNum, 'people cant have']).

%  b) Same as a above, except we use what we know to be true
pickSuspect(Suspect, Weapon, Room) :- 
   % Get number of players who don't have some suspect S
   possibleSuspects(Possible),
   setof(N, S^(suspect(S), member(S, Possible), numCantHave(S, N)), Nums),
   last(Nums, MaxNum),
   % Now get the list of all supects that have MaxNum as the
   % number of people who can't have them
   setof(S2, (suspect(S2), member(S2, Possible), numCantHave(S2, MaxNum)), PickFrom),
   % Pick a random element from that list as long as it's
   % got elements in it!
   randomElement(PickFrom, Suspect),
   \+ isTelferDillow(Suspect, Weapon, Room),
   writeLog(['s4 suspect unknown, pick random suspect from',
            PickFrom, 'I know', MaxNum, 'people cant have']).

% Same as above, ignore Telfer-Dillow
pickSuspect(Suspect, _Weapon, _Room) :- 
   % Get number of players who don't have some suspect S
   possibleSuspects(Possible),
   setof(N, S^(suspect(S), member(S, Possible), numCantHave(S, N)), Nums),
   last(Nums, MaxNum),
   % Now get the list of all supects that have MaxNum as the
   % number of people who can't have them
   setof(S2, (suspect(S2), member(S2, Possible), numCantHave(S2, MaxNum)), PickFrom),
   % Pick a random element from that list as long as it's
   % got elements in it!
   randomElement(PickFrom, Suspect),
   writeLog(['s5 suspect unknown, pick random suspect from',
            PickFrom, 'I know', MaxNum, 'people cant have']).


%  c) Probably some bad inferences, so choose from all unknown
pickSuspect(Suspect, _Weapon, _Room) :- 
   possibleSuspects(Possible), 
   randomElement(Possible, Suspect),
   writeLog(['s99 suspect unknown, pick random']).


% PickSuspect/4 rules to use if I know the suspect
%    a) pick a suspect that I have but have not shown but have suggested
pickSuspect(Suspect, _Weapon, _Room, _TheSuspect) :-  iAm(Me),
   setof(S, P^W^R^(suspect(S), iHave(S), \+iShowed(P, S),
             suggested(Me, S, W, R)), NotShown),
   randomElement(NotShown, Suspect),
   writeLog(['s90 pick suspect I have and have not shown but have suggested']).

%    b) pick a suspect that I have and have suggested
pickSuspect(Suspect, _Weapon, _Room, _TheSuspect) :- iAm(Me), 
   setof(S, W^R^(suspect(S), iHave(S), suggested(Me,S,W,R)), Suggested),
   randomElement(Suggested, Suspect),
   writeLog(['s91 pick suspect I have and have suggested']).

%    b) pick a suspect that I have but have not shown
pickSuspect(Suspect, _Weapon, _Room, _TheSuspect) :-  
   setof(S, P^(suspect(S), iHave(S), \+iShowed(P, S)), NotShown),
   randomElement(NotShown, Suspect),
   writeLog(['s92 pick suspect I have and have not shown']).

%    d) pick a suspect that I have
pickSuspect(Suspect, _Weapon, _Room, _TheSuspect) :- 
   setof(S, (suspect(S), iHave(S)), NotShown),
   randomElement(NotShown, Suspect),
   writeLog(['s93 pick suspect I have']).

%    e) pick the suspect itself - no point picking something someone will show 
%       me again since I need to find out other information
pickSuspect(Suspect, _Weapon, _Room, Suspect) :- 
   writeLog(['s94 I have none in my hand so pick THE suspect']).


%%%%%%%%%%%%%%%
% Helper predicate to tell me how many different players, other than myself,
% have refuted a suggestion with a particular unknown suspect
%%%%%%%%%%%%%%%%  Unused right now
suspectRefuted(Suspect, Number) :- 
   findall(R, (refuted(R, P, Suspect, _, _), 
               \+ iAm(R), \+ iAm(P),
               \+ hasCard(_, Suspect)), 
           Ref),
   list_to_set(Ref, Refuted), length(Refuted, Number).


%%%%%%%%%%%%%%%
% Helper predicate that takes a list of cards and finds the active player
% furthest from me that may have the card (i.e., we believe it's
% likely they do not have the card). Used in pickRoom
% when we're surrounded by known rooms but want to make a suggestion
% that will go some way before it gets disproved
% %%%%%%%%%%%%%%
% Recursive version that sees if a card likely cant be had by anyone X away from me. Distance
% will be bound to the maximum value that any card had 
furthestPlayerFromMeCantDisprove(Players, Cards, Card, Distance) :- 
   Distance>0, 
   member(Card, Cards), 
   playerAwayCantHave(Players, Card, Distance).

% Try a player closer to me
furthestPlayerFromMeCantDisprove(Players, Cards, Card, Distance) :- Distance>1, Closer is Distance-1,
   furthestPlayerFromMeCantDisprove(Players, Cards, Card, Closer). 

playerAwayCantHave(_, _, 0).
playerAwayCantHave(Players, Card, Distance) :- 
   iAm(Me), length(Players, NumPlayers),
   nth0(MyIndex, Players, Me), Far is mod(MyIndex+Distance, NumPlayers), 
   nth0(Far, Players, FarPlayer), 
   (likelyCantHave(FarPlayer, Card); \+likelyHasCard(FarPlayer, Card)),
   Next is Distance-1,
   playerAwayCantHave(Players, Card, Next).






%%%%%%%%%%%%%%%%
% Helper predicate to pick a random element from a list
%%%%%%%%%%%%%%%%
randomElement(List, RandomElement) :-
   % Make sure the list is longer than 0
   length(List, MaxInt), MaxInt > 0,
   % Pick a random number 0 <= R <= MaxInt
   Index is random(MaxInt),
   % Use that to index into the list and pick an element
   nth0(Index, List, RandomElement).






%%%%%%%%%%%%%%%%
% nextRoom() finds a path from X to Y and returns the next room we should
% move to in order to move from X to Y. The distance()
% relation returns the distance between rooms
%%%%%%%%%%%%%%%

% Find a path from X to Y as a list [Y..X] and our next room is 
% the next-to-last item in that path list - note that this works
% correctly for immediately adjacent rooms as well since the list will 
% have two elements
nextRoom(Current, Destination, Next) :-
   breadthFirst([[Current] | Temp] - Temp, Path, Destination),
   length(Path, L), Index is L-1, nth1(Index, Path, Next).

% Get a list of the distances to a set of locations
distances([From], Destination, [Dist]) :-
   distance(From, Destination, Dist).
distances([From | Rest], Destination, [Dist | Distances]) :-
   distance(From, Destination, Dist),
   distances(Rest, Destination, Distances).

% Distance is length of the path - 1
distance(Current, Destination, Length) :-
   breadthFirst([[Current] | Temp] - Temp, Path, Destination),
   length(Path, L), Length is L-1.



%%%%%%%%%%%%%%%%%%%
% Breadth-first search
%
% General breadth-first search returns shortest path between two nodes
% where distances are uniform.  
%%%%%%%%%%%%%%%%%%%%

% If the head of the list for the path is the goal node, then we have a path and
% are done!
% breadthFirst([[Node | Path]|_] - _, [Node | Path], Goal) :- Goal = Node.
breadthFirst([[Goal | Path]|_] - _, [Goal | Path], Goal).

% Otherwise we need to extend the path with adjacent nodes
breadthFirst([Path | Paths] - Z, Solution, Goal) :-
   extend(Path, NewPaths),
   append(NewPaths, Z1, Z),
   Paths \== Z1,
   breadthFirst(Paths - Z1, Solution, Goal).
        
% Paths are extended with nodes that are adjacent
extend([Node | Path], NewPaths) :- 
   bagof([NewNode, Node | Path],
              (adjacent(Node, NewNode), 
                 \+ member(NewNode, [Node | Path])), %no cycles
              NewPaths), !.
% Otherwise, we are at a dead end - no way to extend the path so we succeed but
% return an emtpy list of new paths
extend(_,[]).


% Sounds to use for certain events
soundFor(init,'afplay ~/Sounds/nthng.mp3').
soundFor(weapon,'afplay ~/Sounds/sweet.mp3').
soundFor(suspect,'afplay ~/Sounds/kyes.mp3').
soundFor(room,'afplay ~/Sounds/bowtoyoursensei.mp3').
soundFor(accuse,'afplay ~/Sounds/smart.mp3').
soundFor(lost,'afplay ~/Sounds/lucky.mp3').

% If this is the first time this kind of card has been seen, play a
% sound and remember that we've seen the kind of card so we don't play
% again
playSoundFor(Event) :- playedSound(Sounds) , \+ member(Event, Sounds),
                       retractall(playedSound(_)),
                       asserta(playedSound([Event|Sounds])),
                       soundFor(Event,SoundToPlay),
                       playSound(SoundToPlay).
playSoundFor(_).

% Turn sound on/off 
%%%playSound(SoundToPlay) :- shell(SoundToPlay).
% If playing fails for some reason, continue anyway.
playSound(_).
