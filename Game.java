import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;

public class Game extends Frame {
	class Rect {
		float x, y, width, height;
		Rect(float x, float y, float width, float height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
	}

	static final long NANOSEC_IN_SEC = 1000000000, NANOSEC_IN_MS = 1000000;

	/* window */
	static final int WIDTH = 256, HEIGHT = 144;
	static final int FPS = 60;
	final Insets winInsets;
	final int winScale;
	boolean winReady;

	/* graphics */
	class Sprite {
		static final int SIZE = 16;

		static final int BIRD            = 0;
		static final int BIRD_FLAPPING   = 1;
		static final int BIRD_HURT       = 2;
		static final int PIPE            = 3;
		static final int PIPE_ENTRY_UP   = 4;
		static final int PIPE_ENTRY_DOWN = 5;
	}
	class AssetsNotFoundException extends Exception {
		AssetsNotFoundException(String message) {
			super(message);
		}
	}
	static final Font fontScore = new Font(Font.DIALOG, Font.PLAIN, 12);
	final BufferedImage backBuffer, spriteSheet;
	final Graphics2D g;

	/* game logic */
	enum State { GAME, GAME_OVER }
	static final float FLIGHT_SPEED = 200;
	static final float FALL_SPEED = 200;
	static final float FLAP_SPEED = FALL_SPEED * 2;
	static final long FLAP_PERIOD_NS = 400000000;
	State state;
	boolean quit, gameOver;
	int score;
	Rect bird;
	float fallSpeed;
	boolean flapping;
	long flapTime, flapRemaining;
	final Queue<Rect> pipeGaps = new LinkedList<>();

	final PrintStream log = System.err;

	/* setup */
	Game(int winScale) throws AssetsNotFoundException {
		super("Flappy");

		log.println("Starting game...");

		if (winScale <= 0)
			winScale = 1;
		this.winScale = winScale;

		setResizable(false);
		setVisible(true);
		winInsets = getInsets();
		int winWidth = WIDTH * winScale + winInsets.left + winInsets.right;
		int winHeight = HEIGHT * winScale + winInsets.top + winInsets.bottom;
		setSize(winWidth, winHeight);
		setLocationRelativeTo(null);

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
					gameBirdFlap();
			}
		});


		backBuffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		g = (Graphics2D)backBuffer.getGraphics();
		g.setPaintMode();

		InputStream is = Game.class.getResourceAsStream("spritesheet.png");
		if (is == null)
			throw new AssetsNotFoundException("[error] could not find assets; maybe you forgot '-cp out:assets' or '-cp out;assets'");
		try {
			if (is.available() <= 0)
				throw new AssetsNotFoundException("[error] 'spritesheet.png' is empty or invalid");
			spriteSheet = ImageIO.read(is);
		} catch (IOException e) {
			throw new AssetsNotFoundException("[error] could not read 'spritesheet.png'");
		}

		quit = false;
		winReady = true;
	}

	void gameBirdFlap() {
		if (gameOver) {
			gameStart();
			return;
		}

		flapping = true;
		flapTime = System.nanoTime();
		if (fallSpeed < FALL_SPEED)
			fallSpeed = FALL_SPEED;
	}

	void gameStart() {
		state = State.GAME;
		gameOver = false;
		score = 0;
		bird = new Rect(WIDTH/6, Sprite.SIZE, Sprite.SIZE, Sprite.SIZE);
		flapTime = 0;
		flapping = false;
		fallSpeed = FALL_SPEED/2;

		pipeGaps.clear();
		for (int i = 0; i < 3; ++i)
			pipeGaps.add(new Rect(WIDTH + Sprite.SIZE*8*i, HEIGHT/2 - Sprite.SIZE*2 + Sprite.SIZE*(1-i), Sprite.SIZE, Sprite.SIZE*4));
	}

	void gameUpdate(long begFrameTime, float deltaTime) {
		if (state == State.GAME_OVER)
			return;


		bird.y += fallSpeed * deltaTime;

		if (flapping) {
			flapRemaining = begFrameTime - flapTime;
			if (flapRemaining <= FLAP_PERIOD_NS)
				bird.y -= FLAP_SPEED * (-((double)flapRemaining/FLAP_PERIOD_NS)+1) * deltaTime;
			else
				flapping = false;
		}

		for (Rect gap : pipeGaps)
			gap.x -= FLIGHT_SPEED * deltaTime;

		Rect gap = pipeGaps.peek();
		if (gap.x + gap.width < 0) {
			gap.x = WIDTH + Sprite.SIZE*8;
			gap.y = ThreadLocalRandom.current().nextInt(Sprite.SIZE/2, HEIGHT - (int)gap.height - Sprite.SIZE/2);
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

	void drawSprite(int sp, Rect r) {
		g.drawImage(spriteSheet, (int)r.x, (int)r.y, (int)r.x+(int)r.width, (int)r.y+(int)r.height,
				(int)r.width*sp, 0, (int)r.width*sp + (int)r.width, (int)r.height, null);
	}

	void drawPipes(Rect gap) {
		for (int offY = (int)gap.y-Sprite.SIZE*2;; offY -= Sprite.SIZE) {
			drawSprite(Sprite.PIPE, new Rect(gap.x, offY, gap.width, Sprite.SIZE));
			if (offY < 0)
				break;
		}	
		drawSprite(Sprite.PIPE_ENTRY_DOWN, new Rect(gap.x, gap.y-Sprite.SIZE, gap.width, Sprite.SIZE));
		drawSprite(Sprite.PIPE_ENTRY_UP, new Rect(gap.x, gap.y+gap.height, gap.width, Sprite.SIZE));
		for (int offY = (int)gap.y+(int)gap.height + Sprite.SIZE;; offY += Sprite.SIZE) {
			drawSprite(Sprite.PIPE, new Rect(gap.x, offY, gap.width, Sprite.SIZE));
			if (offY >= HEIGHT)
				break;
		}	
	}

	void drawScore() {
		g.setFont(fontScore);
		g.setColor(Color.WHITE);
		g.drawString(Integer.toString(score), 4, 4 + fontScore.getSize());
	}

	void run() throws RuntimeException {
		final long frameInterval = NANOSEC_IN_SEC/FPS;
		long begFrameTime, endFrameTime, remainingTime;
		float deltaTime = (float)frameInterval / NANOSEC_IN_SEC;

		gameStart();

		try {
			while (!quit) {
				begFrameTime = System.nanoTime();

				gameUpdate(begFrameTime, deltaTime);
				gameDraw();

				if (!gameOver && state == State.GAME_OVER) {
					Thread.sleep(200);
					gameOver = true;
					continue;
				}

				endFrameTime = System.nanoTime();
				remainingTime = begFrameTime + frameInterval - endFrameTime;
				if (remainingTime > 0) {
					Thread.sleep(remainingTime/NANOSEC_IN_MS);
					deltaTime = (float)frameInterval / NANOSEC_IN_SEC;
				} else {
					deltaTime = (float)(endFrameTime - begFrameTime) / NANOSEC_IN_SEC;
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("[error] unexpected interrupt");
		}

		log.println("Bye!");
		dispose();
	}

	@Override
	public void update(Graphics g) {
		if (!winReady)
			return;

		drawRect(new Color(0x71B2DC), new Rect(0, 0, WIDTH, HEIGHT));
		for (Rect gap : pipeGaps)
			drawPipes(gap);
		if (state == State.GAME_OVER)
			drawSprite(Sprite.BIRD_HURT, bird);
		else if (flapping && flapRemaining <= FLAP_PERIOD_NS * 0.5f)
			drawSprite(Sprite.BIRD_FLAPPING, bird);
		else
			drawSprite(Sprite.BIRD, bird);
		drawScore();

		g.drawImage(backBuffer, winInsets.left, winInsets.top, WIDTH * winScale, HEIGHT * winScale, null);
	}

	@Override
	public void paint(Graphics g) {
		update(g);
	}

	public static void main(String args[]) {
		int winScale = (args.length == 1) ? Integer.parseInt(args[0]) : 3;
		try {
			Game game = new Game(winScale);
			game.run();
		} catch (AssetsNotFoundException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		} catch (RuntimeException e) {
			System.err.println(e.getMessage());
			System.exit(2);
		}
	}
}
