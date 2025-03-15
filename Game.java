import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

public class Game extends Frame {
	enum Sprite { BIRD, BIRD_FLAPPING, BIRD_HURT, PIPE, PIPE_ENTRY }

	enum State { GAME, GAME_OVER }

	/* TEMP */
	class GameColor {
		static final Color SKY = new Color(0x71B2DC);
		static final Color SCORE = Color.WHITE;
		static final Color BIRD = Color.ORANGE;
		static final Color BIRD_FLAPPING = Color.RED;
		static final Color BIRD_HURT = Color.MAGENTA;
		static final Color PIPE = new Color(0x4BB575);
		static final Color PIPE_SHADOW = new Color(0x6BA8CF);
	}

	class Rect {
		float x, y, width, height;
		Rect(float x, float y, float width, float height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
	}

	/* window */
	final int WIDTH = 256, HEIGHT = 144;
	final int WIN_SCALE = 3;
	final int FPS = 60;
	final long NANOSEC_TO_SEC = 1000000000, NANOSEC_TO_MS = 1000000;
	float deltaTime;

	/* graphics */
	final int TILE_SIZE = 16;
	final Font fontScore;
	final BufferedImage backBuffer;
	final Graphics2D g;

	/* game logic */
	State state;
	boolean quit, gameOver;
	int score;
	Rect bird;
	float birdFallSpeed;
	long birdJumpTime, deltaJumpTime;
	final long FLAP_PERIOD_NS = 400000000;
	final float FLIGHT_SPEED = 110;
	final float FALL_SPEED = 200;
	final float FLAP_SPEED = FALL_SPEED * 2;
	final Queue<Rect> pipeGaps = new LinkedList<>();

	final PrintStream log = System.err;

	/* setup */
	Game() {
		super("Flappy");

		log.println("Starting game...");

		GraphicsConfiguration gc = getGraphicsConfiguration();
		Rectangle bounds = gc.getBounds();
		log.printf("[debug] gc bounds: %dx%d (%d,%d)\n", bounds.width, bounds.height, bounds.x, bounds.y);

		int winWidth = WIDTH * WIN_SCALE;
		int winHeight = HEIGHT * WIN_SCALE;
		Point winLoc = new Point(bounds.x + bounds.width/2 - winWidth/2, bounds.y + bounds.height/2 - winHeight/2);
		setSize(winWidth, winHeight);
		setLocation(winLoc);
		log.printf("[debug] win bounds: %dx%d (%d,%d)\n", winWidth, winHeight, winLoc.x, winLoc.y);

		setResizable(false);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent we) {
				quit = true;
			}
		});

		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				int key = e.getKeyCode();
				if (key == KeyEvent.VK_SPACE
				||  key == KeyEvent.VK_UP)
					gameBirdJump();
			}
		});

		fontScore = new Font(Font.DIALOG, Font.PLAIN, 12);

		backBuffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		g = (Graphics2D)backBuffer.getGraphics();
		g.setPaintMode();

		quit = false;
	}

	void gameBirdJump() {
		if (gameOver) {
			gameStart();
			return;
		}

		birdJumpTime = System.nanoTime();
		if (birdFallSpeed < FALL_SPEED)
			birdFallSpeed = FALL_SPEED;
	}

	void gameStart() {
		log.printf("[debug] game start (last score: %d)\n", score);

		state = State.GAME;
		gameOver = false;
		score = 0;
		bird = new Rect(WIDTH/6, TILE_SIZE, TILE_SIZE, TILE_SIZE);
		birdJumpTime = 0;
		birdFallSpeed = FALL_SPEED/2;

		pipeGaps.clear();
		for (int i = 0; i < 3; ++i)
			pipeGaps.add(new Rect(WIDTH + TILE_SIZE*8*i, HEIGHT/2 - TILE_SIZE*2 + TILE_SIZE*(1-i), TILE_SIZE, TILE_SIZE*4));
	}

	void gameUpdate(long begFrameTime) {
		if (state == State.GAME_OVER)
			return;

		deltaJumpTime = begFrameTime - birdJumpTime;

		bird.y += birdFallSpeed * deltaTime;

		if (deltaJumpTime <= FLAP_PERIOD_NS)
			bird.y -= FLAP_SPEED * (-((double)deltaJumpTime/FLAP_PERIOD_NS)+1) * deltaTime;

		for (Rect gap : pipeGaps)
			gap.x -= FLIGHT_SPEED * deltaTime;

		Rect gap = pipeGaps.peek();
		if (gap.x + gap.width < 0) {
			gap.x = WIDTH + TILE_SIZE*8;
			gap.y = ThreadLocalRandom.current().nextInt(TILE_SIZE/2, HEIGHT - (int)gap.height - TILE_SIZE/2);
			pipeGaps.remove();
			pipeGaps.add(gap);

			++score;
		}

		if (bird.y >= HEIGHT) {
			state = State.GAME_OVER;
			return;
		}

		gap = pipeGaps.peek();
		if (bird.x+bird.width < gap.x || bird.x > gap.x+gap.width)
			return;

		if (bird.y <= gap.y || bird.y+bird.height >= gap.y+gap.height)
			state = State.GAME_OVER;
	}

	void gameDraw() {
		revalidate();
		repaint();
	}

	void drawRect(Color c, Rect r) {
		g.setColor(c);
		g.fillRect((int)r.x, (int)r.y, (int)r.width, (int)r.height);
	}

	void drawSprite(Sprite sp, Rect r) {
		Color c;
		switch (sp) {
			case BIRD:
				c = GameColor.BIRD;
				break;
			case BIRD_FLAPPING:
				c = GameColor.BIRD_FLAPPING;
				break;
			case BIRD_HURT:
				c = GameColor.BIRD_HURT;
				break;
			case PIPE, PIPE_ENTRY:
				c = GameColor.PIPE;
				break;
			default:
				c = null;
		}
		drawRect(c, r);
	}

	void drawPipes(Rect gap) {
		drawRect(GameColor.PIPE, new Rect(gap.x, 0, gap.width, gap.y));
		drawRect(GameColor.PIPE_SHADOW, gap);
		drawRect(GameColor.PIPE, new Rect(gap.x, gap.y+gap.height, gap.width, HEIGHT-(gap.y+gap.height)));
	}

	void drawScore() {
		g.setFont(fontScore);
		g.setColor(GameColor.SCORE);
		g.drawString(Integer.toString(score), 4, 4 + fontScore.getSize());
	}

	void run() throws InterruptedException {
		final long frameInterval = NANOSEC_TO_SEC/FPS;
		long begFrameTime, endFrameTime, remainingTime;

		setVisible(true);
		gameStart();

		while (!quit) {
			begFrameTime = System.nanoTime();

			gameUpdate(begFrameTime);
			gameDraw();

			if (!gameOver && state == State.GAME_OVER) {
				Thread.sleep(200);
				gameOver = true;
				continue;
			}

			endFrameTime = System.nanoTime();
			remainingTime = begFrameTime + frameInterval - endFrameTime;
			if (remainingTime > 0) {
				Thread.sleep(remainingTime/NANOSEC_TO_MS);
				deltaTime = (float)frameInterval / NANOSEC_TO_SEC;
			} else {
				deltaTime = (float)(endFrameTime - begFrameTime) / NANOSEC_TO_SEC;
			}
		}

		log.println("Bye!");
		dispose();
	}

	@Override
	public void update(Graphics g) {
		drawRect(GameColor.SKY, new Rect(0, 0, WIDTH, HEIGHT));
		for (Rect gap : pipeGaps)
			drawPipes(gap);
		if (state == State.GAME_OVER)
			drawSprite(Sprite.BIRD_HURT, bird);
		else if (deltaJumpTime <= FLAP_PERIOD_NS * 0.5f)
			drawSprite(Sprite.BIRD_FLAPPING, bird);
		else
			drawSprite(Sprite.BIRD, bird);
		drawScore();
		g.drawImage(backBuffer, 0, 0, WIDTH * WIN_SCALE, HEIGHT * WIN_SCALE, null);
	}

	@Override
	public void paint(Graphics g) {
		update(g);
	}

	public static void main(String args[]) {
		Game game = new Game();
		try {
			game.run();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
