package com.example.demobase.service;

import com.example.demobase.dto.GameDTO;
import com.example.demobase.dto.GameResponseDTO;
import com.example.demobase.model.Game;
import com.example.demobase.model.GameInProgress;
import com.example.demobase.model.Player;
import com.example.demobase.model.Word;
import com.example.demobase.repository.GameInProgressRepository;
import com.example.demobase.repository.GameRepository;
import com.example.demobase.repository.PlayerRepository;
import com.example.demobase.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameService {
    
    private final GameRepository gameRepository;
    private final GameInProgressRepository gameInProgressRepository;
    private final PlayerRepository playerRepository;
    private final WordRepository wordRepository;
    
    private static final int MAX_INTENTOS = 7;
    private static final int PUNTOS_PALABRA_COMPLETA = 20;
    private static final int PUNTOS_POR_LETRA = 1;
    
    @Transactional
    public GameResponseDTO startGame(Long playerId) {
        GameResponseDTO response = new GameResponseDTO();
        // DO: Implementar el metodo startGame
        // Validar que el jugador existe
        if(playerRepository.findById(playerId).isEmpty()){
            throw  new IllegalArgumentException("El player no existe en el sistema");
        }

        // Verificar si ya existe una partida en curso para este jugador y palabra


        Word word = wordRepository.findRandomWord()
                .orElseThrow(() -> new IllegalArgumentException("No hay palabras disponibles"));

        List<GameInProgress> gameInProgress = gameInProgressRepository.findByJugadorIdOrderByFechaInicioDesc(playerId);

        for (GameInProgress gameInProgres : gameInProgress) {
            if(playerId.equals( gameInProgres.getJugador().getId())){
                if(word.equals( gameInProgres.getPalabra())){
                    throw  new RuntimeException("La letra  ya fue utilizada ");
                }
            }

        }
        // Marcar la palabra como utilizada
        word.setUtilizada(true);
        wordRepository.save(word);

        // Crear nueva partida en curso

        GameInProgress gameInProgres = new GameInProgress();
        gameInProgres.setJugador(playerRepository.findById(playerId).get());
        gameInProgres.setPalabra(word);
        gameInProgres.setIntentosRestantes(MAX_INTENTOS);
        gameInProgres.setLetrasIntentadas("");
        gameInProgres.setFechaInicio(LocalDateTime.now());
        gameInProgressRepository.save(gameInProgres);

        response= buildResponseFromGameInProgress(gameInProgres);
        return response;
    }
    
    @Transactional
    public GameResponseDTO makeGuess(Long playerId, Character letra) {
        GameResponseDTO response = new GameResponseDTO();
        // TODO: Implementar el método makeGuess
        // Validar que el jugador existe
        if(playerRepository.findById(playerId).isEmpty()){
            throw  new IllegalArgumentException("El player no existe en el sistema");
        }

        // Convertir la letra a mayúscula
        Character letraa = Character.toUpperCase(letra);


        // Buscar la partida en curso más reciente del jugador
        // Tomar la partida más reciente
        GameInProgress gameInProgress = gameInProgressRepository.findByJugadorIdOrderByFechaInicioDesc(playerId).get(0);


        // Obtener letras ya intentadas
        Set<Character> LetrasIntentadas = stringToCharSet(gameInProgress.getLetrasIntentadas());

        // Verificar si la letra ya fue intentada
        // Agregar la nueva letra
        if(LetrasIntentadas.contains(letraa)){
        }else {
            LetrasIntentadas.add(letraa);
            gameInProgress.setLetrasIntentadas(charSetToString(LetrasIntentadas));
        }


        // Verificar si la letra está en la palabra
        // Decrementar intentos solo si la letra es incorrecta

        Word word = gameInProgress.getPalabra();

        if(word.getPalabra().indexOf(letraa)==-1){

            gameInProgress.setIntentosRestantes(gameInProgress.getIntentosRestantes()-1);
        }

        // Generar palabra oculta
        // Guardar el estado actualizado
        // Si el juego terminó, guardar en Game y eliminar de GameInProgress

        String palabrafinal = generateHiddenWord(gameInProgress.getPalabra().getPalabra().toUpperCase(),LetrasIntentadas);
        if(!palabrafinal.contains("_")){
            Player player1 = playerRepository.findById(playerId).get();

            saveGame(player1,word,true,1);
            gameInProgressRepository.delete(gameInProgress);
        }
        gameInProgress.setPalabra(word);


        // Construir respuesta

        response= buildResponseFromGameInProgress(gameInProgress);
        return response;
    }
    
    private GameResponseDTO buildResponseFromGameInProgress(GameInProgress gameInProgress) {
        String palabra = gameInProgress.getPalabra().getPalabra().toUpperCase();
        Set<Character> letrasIntentadas = stringToCharSet(gameInProgress.getLetrasIntentadas());
        String palabraOculta = generateHiddenWord(palabra, letrasIntentadas);
        boolean palabraCompleta = palabraOculta.equals(palabra);
        
        GameResponseDTO response = new GameResponseDTO();
        response.setPalabraOculta(palabraOculta);
        response.setLetrasIntentadas(new ArrayList<>(letrasIntentadas));
        response.setIntentosRestantes(gameInProgress.getIntentosRestantes());
        response.setPalabraCompleta(palabraCompleta);
        
        int puntaje = calculateScore(palabra, letrasIntentadas, palabraCompleta, gameInProgress.getIntentosRestantes());
        response.setPuntajeAcumulado(puntaje);
        
        return response;
    }
    
    private Set<Character> stringToCharSet(String str) {
        Set<Character> set = new HashSet<>();
        if (str != null && !str.isEmpty()) {
            String[] chars = str.split(",");
            for (String c : chars) {
                if (!c.trim().isEmpty()) {
                    set.add(c.trim().charAt(0));
                }
            }
        }
        return set;
    }
    
    private String charSetToString(Set<Character> set) {
        if (set == null || set.isEmpty()) {
            return "";
        }
        return set.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
    
    private int calculateScore(String palabra, Set<Character> letrasIntentadas, boolean palabraCompleta, int intentosRestantes) {
        if (palabraCompleta) {
            return PUNTOS_PALABRA_COMPLETA;
        } else if (intentosRestantes == 0) {
            // Contar letras correctas encontradas
            long letrasCorrectas = letrasIntentadas.stream()
                    .filter(letra -> palabra.indexOf(letra) >= 0)
                    .count();
            return (int) (letrasCorrectas * PUNTOS_POR_LETRA);
        }
        return 0;
    }
    
    private String generateHiddenWord(String palabra, Set<Character> letrasIntentadas) {
        StringBuilder hidden = new StringBuilder();
        for (char c : palabra.toCharArray()) {
            if (letrasIntentadas.contains(c) || c == ' ') {
                hidden.append(c);
            } else {
                hidden.append('_');
            }
        }
        return hidden.toString();
    }
    
    @Transactional
    public void saveGame(Player player, Word word, boolean ganado, int puntaje) {
        // Asegurar que la palabra esté marcada como utilizada
        if (!word.getUtilizada()) {
            word.setUtilizada(true);
            wordRepository.save(word);
        }
        
        Game game = new Game();
        game.setJugador(player);
        game.setPalabra(word);
        game.setResultado(ganado ? "GANADO" : "PERDIDO");
        game.setPuntaje(puntaje);
        game.setFechaPartida(LocalDateTime.now());
        gameRepository.save(game);
    }
    
    public List<GameDTO> getGamesByPlayer(Long playerId) {
        return gameRepository.findByJugadorId(playerId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<GameDTO> getAllGames() {
        return gameRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    private GameDTO toDTO(Game game) {
        GameDTO dto = new GameDTO();
        dto.setId(game.getId());
        dto.setIdJugador(game.getJugador().getId());
        dto.setNombreJugador(game.getJugador().getNombre());
        dto.setResultado(game.getResultado());
        dto.setPuntaje(game.getPuntaje());
        dto.setFechaPartida(game.getFechaPartida());
        dto.setPalabra(game.getPalabra() != null ? game.getPalabra().getPalabra() : null);
        return dto;
    }
}

