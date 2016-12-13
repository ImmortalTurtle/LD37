package com.jacktheogre.lightswitch.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jacktheogre.lightswitch.LightSwitch;
import com.jacktheogre.lightswitch.ai.LevelManager;
import com.jacktheogre.lightswitch.ai.Node;
import com.jacktheogre.lightswitch.commands.AddTeleportCommand;
import com.jacktheogre.lightswitch.commands.CommandHandler;
import com.jacktheogre.lightswitch.objects.InteractiveObject;
import com.jacktheogre.lightswitch.objects.Teleport;
import com.jacktheogre.lightswitch.sprites.Button;
import com.jacktheogre.lightswitch.sprites.EnemyPlayer;
import com.jacktheogre.lightswitch.sprites.Player;
import com.jacktheogre.lightswitch.tools.AssetLoader;
import com.jacktheogre.lightswitch.tools.Assets;
import com.jacktheogre.lightswitch.tools.B2WorldCreator;
import com.jacktheogre.lightswitch.tools.GenerateInputHandler;
import com.jacktheogre.lightswitch.tools.Lighting;

/**
 * Created by luna on 10.12.16.
 */
public class GeneratingScreen implements Screen{
    private final Color CORRECT = new Color(0, 1, 0, 0.5f);
    private final Color WRONG = new Color(1, 0, 0, 0.5f);

    private final EnemyPlayer enemyPlayer;
    private final Player player;
    private Lighting lighting;
    private OrthographicCamera gameCam;
//    private OrthographicCamera staticCam;
    private Viewport gamePort;
    private LightSwitch game;
    public Array<InteractiveObject> objects;
    private AssetLoader loader;
    private ShapeRenderer shapeRenderer;
    private OrthogonalTiledMapRenderer mapRenderer;
    private World world;
    private CommandHandler commandHandler;
    private Button undo, redo, start, teleportButton;

    private Node selectedNode;

    private final Color BACKGROUND_COLOR = new Color(56/255f, 56/255f, 113/255f, 1f);

    public GeneratingScreen(LightSwitch game) {
        this.game = game;
        gameCam = new OrthographicCamera();
//        staticCam = new OrthographicCamera();
        gamePort = new FitViewport(LightSwitch.WIDTH, LightSwitch.HEIGHT, gameCam);
        // TODO: 12.12.16 make map in the middle
        gameCam.position.set(gamePort.getWorldWidth() / 2, gamePort.getWorldHeight() / 2, 0);
        gameCam.zoom -= 0.2f;

        loader = Assets.getAssetLoader();
        LevelManager.loadLevel(loader.map);
        mapRenderer = new OrthogonalTiledMapRenderer(loader.map);

        undo = new Button(Assets.getAssetLoader().undo_button, Button.State.ACTIVE);
        redo = new Button(Assets.getAssetLoader().redo_button, Button.State.ACTIVE);
        start = new Button(Assets.getAssetLoader().start_button, Button.State.ACTIVE);
        teleportButton = new Button(Assets.getAssetLoader().teleport_button, Button.State.ACTIVE);
        undo.setPosition(10, -30);
        redo.setPosition(undo.getX() + undo.getWidth()+15, undo.getY());
        start.setPosition(redo.getX() + redo.getWidth()+15, redo.getY());
        teleportButton.setPosition(-teleportButton.getWidth() - 10, 100);

        world = new World(new Vector2(0, 0), true);
        objects = new Array<InteractiveObject>();

        player = new Player(this);
        enemyPlayer = new EnemyPlayer(this);

        LevelManager.loadLevel(loader.map);
        lighting = new Lighting(this);
        new B2WorldCreator(this);

        shapeRenderer = new ShapeRenderer();
        shapeRenderer.setProjectionMatrix(gameCam.combined);
        shapeRenderer.setAutoShapeType(true);
        commandHandler = new CommandHandler(this);
        Node.Indexer.nullify();
        Gdx.input.setInputProcessor(new GenerateInputHandler(this));
        // TODO: 12.12.16 look next string 
//        player.update(0);
    }

    public void update(float dt){
        commandHandler.update(dt);
        if(commandHandler.newCommands())
            commandHandler.executeCommandsGenerate();
        world.step(1/60f, 6, 2);

        lerpCamera(gamePort.getWorldWidth() / 4, gamePort.getWorldHeight() / 4, dt);
        gameCam.update();

        mapRenderer.setView(gameCam);
    }

    private void lerpCamera(float targetX, float targetY, float dt) {
        float lerp;
        lerp = 10f;
        Vector3 position = gameCam.position;
        position.x += (targetX - position.x) * lerp * dt;
        position.y += (targetY - position.y) * lerp * dt;
    }

    @Override
    public void render(float delta) {
        shapeRenderer.setProjectionMatrix(gameCam.combined);
        shapeRenderer.setAutoShapeType(true);
        update(delta);

        Gdx.gl.glClearColor(BACKGROUND_COLOR.r,BACKGROUND_COLOR.g, BACKGROUND_COLOR.b, BACKGROUND_COLOR.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        mapRenderer.render();

        game.batch.setProjectionMatrix(gameCam.combined);
        game.batch.begin();
        game.batch.draw(Assets.getAssetLoader().moon, Assets.getAssetLoader().moon.getWidth()-gamePort.getWorldWidth() / 4, -gameCam.position.y / 2);
        //buttons
        undo.draw(game.batch);
        redo.draw(game.batch);
        start.draw(game.batch);
//        teleportButton.draw(game.batch);

        for (InteractiveObject object : objects) {
            object.render(game.batch);
        }
        game.batch.end();

        renderSelected();

        game.batch.begin();
        for (InteractiveObject object :objects) {
            object.render(game.batch);
        }
        player.getActor().draw(game.batch);
        if(lighting.lightsOn())
            enemyPlayer.getEnemy().draw(game.batch);
        game.batch.end();
//        LevelManager.graph.render(shapeRenderer);

    }

    public void renderSelected() {
        Gdx.graphics.getGL20().glEnable(GL20.GL_BLEND);
        if(selectedNode == null) {
            return;
        }
        if(selectedNode.getConnections().size > 0) {
            shapeRenderer.setColor(CORRECT);
        } else {
            shapeRenderer.setColor(WRONG);
        }
        if(!shapeRenderer.isDrawing())
            shapeRenderer.begin();
        shapeRenderer.set(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.rect(selectedNode.getWorldX() - LevelManager.tilePixelWidth / 2, selectedNode.getWorldY() - LevelManager.tilePixelHeight / 2,
                LevelManager.tilePixelWidth, LevelManager.tilePixelHeight);
        shapeRenderer.end();
    }

    public Button getUndo() {
        return undo;
    }

    public Button getRedo() {
        return redo;
    }

    public Button getStart() {
        return start;
    }

    public Button getTeleportButton() {
        return teleportButton;
    }

    public void setSelectedNode(Node selectedNode) {
        this.selectedNode = selectedNode;
    }

    public void addTeleport() {
        if(selectedNode.getConnections().size > 0)
            commandHandler.addCommand(new AddTeleportCommand(this, (int) selectedNode.getWorldX() - LevelManager.tilePixelWidth / 2, (int)selectedNode.getWorldY() - LevelManager.tilePixelHeight / 2, objects));
        Gdx.app.log("objects", ""+objects.size);
    }

    public EnemyPlayer getEnemyPlayer() {
        return enemyPlayer;
    }

    public Player getPlayer() {
        return player;
    }

    public OrthographicCamera getGameCam() {
        return gameCam;
    }

    public Viewport getGamePort() {
        return gamePort;
    }

    public LightSwitch getGame() {
        return game;
    }

    public Array<InteractiveObject> getObjects() {
        return objects;
    }

    public AssetLoader getLoader() {
        return loader;
    }

    public ShapeRenderer getShapeRenderer() {
        return shapeRenderer;
    }

    public OrthogonalTiledMapRenderer getMapRenderer() {
        return mapRenderer;
    }

    public World getWorld() {
        return world;
    }

    public CommandHandler getCommandHandler() {
        return commandHandler;
    }

    @Override
    public void show() {

    }

    @Override
    public void resize(int width, int height) {
        gamePort.update(width, height);
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void hide() {

    }

    @Override
    public void dispose() {

    }

    public Lighting getLighting() {
        return lighting;
    }

    public void setObjects(Array<InteractiveObject> objects) {
        this.objects = objects;
    }
}
