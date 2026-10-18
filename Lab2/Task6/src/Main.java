/**
 * Модель із розгалуженням маршруту та поверненням заявок до попереднього блока.
 */
public class Main {
    public static void main(String[] args) {
        // У кожному Process є два однакові пристрої та одна спільна черга на п’ять місць.
        Create create = new Create(2.0);
        // Аргументи Process: середній час, місця очікування, кількість пристроїв.
        Process process1 = new Process(1.0, 5, 2);
        Process process2 = new Process(1.0, 5, 2);
        Process process3 = new Process(1.0, 5, 2);
        Dispose dispose = new Dispose();

        create.next = process1;
        // Після P1 вибираємо один маршрут: 70% до P2, 30% одразу до P3.
        process1.setNextElements(
                new Element[]{process2, process3},
                new double[]{0.7, 0.3});
        // Один вихід можна задавати так само, як у попередніх завданнях.
        process2.next = process3;
        // Після P3: 80% заявок виходять, 20% повертаються на повторне обслуговування.
        process3.setNextElements(
                new Element[]{dispose, process1},
                new double[]{0.8, 0.2});

        // При однакових часах подій пріоритет визначає порядок елементів у масиві.
        Model model = new Model(process3, process2, process1, create, dispose);
        model.simulate(1000.0);

        System.out.println("Created: " + create.quantity);
        printProcess("Process 1", process1);
        printProcess("Process 2", process2);
        printProcess("Process 3", process3);
        System.out.println("Disposed: " + dispose.quantity);
    }

    private static void printProcess(String name, Process process) {
        System.out.println("\n" + name);
        // Одна заявка може відвідати блок кілька разів: рахуємо завершення обслуговувань.
        System.out.println("Served (including repeat visits): " + process.quantity);
        System.out.println("Rejected: " + process.failure);
        System.out.println("In queue: " + process.queue);
        System.out.println("Busy servers: " + process.busyCount + "/" + process.getChannelCount());
        System.out.printf("Mean utilization: %.2f%%%n", process.getMeanLoad() * 100);
    }
}

/**
 * Спільна основа елементів моделі.
 * Model вибирає елемент із найменшим nextTime, переводить час до його події
 * та викликає outAct(). Нащадки реалізують власну поведінку цієї події.
 */
abstract class Element {
    // Поточний модельний час. Перед подією Model оновлює його для всіх елементів.
    protected double time;
    // Час наступної події елемента. Нескінченність означає, що події не заплановано.
    protected double nextTime = Double.POSITIVE_INFINITY;
    // Середній інтервал надходження або середня тривалість обслуговування.
    protected final double meanDelay;
    // Наступний блок маршруту, якому передаємо заявку.
    protected Element next;
    // Кількість створених, обслугованих або вихідних заявок залежно від класу.
    protected int quantity;

    Element(double meanDelay) {
        this.meanDelay = meanDelay;
    }

    /**
     * Випадкова затримка за експоненційним розподілом: -meanDelay * ln(u).
     * u має бути в інтервалі (0; 1), тому нуль генеруємо повторно.
     * Однаковий meanDelay у пристроїв не означає однакової затримки кожної заявки.
     */
    protected double getDelay() {
        double u;
        do {
            u = Math.random();
        } while (u == 0.0);
        return -meanDelay * Math.log(u);
    }

    /**
     * Приймання заявки з попереднього блока.
     * Базовий метод порожній; Process і Dispose визначають власні дії.
     */
    public void inAct() { }

    // Збираємо статистику минулого проміжку до того, як подія змінить стан.
    public void doStatistics(double delta) { }

    /**
     * Виконання запланованої події: для Create це надходження,
     * для Process — завершення обслуговування на одному пристрої.
     */
    public abstract void outAct();
}

/**
 * Генератор заявок. Його подія — надходження нової заявки.
 */
class Create extends Element {
    Create(double meanDelay) {
        super(meanDelay);
        // Перша заявка надходить у момент 0.
        nextTime = 0.0;
    }

    @Override
    public void outAct() {
        // Рахуємо нову заявку.
        quantity++;
        // Плануємо наступне надходження: поточний час плюс випадкова затримка.
        nextTime = time + getDelay();
        // Передаємо створену заявку першому блоку Process.
        next.inAct();
    }
}

/**
 * Блок із кількома однаковими пристроями та спільною обмеженою чергою.
 * Кожен пристрій має свій час завершення в finishTimes.
 * nextTime блока — найменший із цих часів.
 */
class Process extends Element {
    // Місця лише для очікування; заявки на пристроях сюди не входять.
    private final int maxQueue;
    // finishTimes[i] — час завершення на пристрої i; нескінченність означає вільний пристрій.
    private final double[] finishTimes;
    // Кількість зайнятих пристроїв, від 0 до finishTimes.length.
    protected int busyCount;
    // Кількість заявок у спільній черзі.
    protected int queue;
    // Відмови: і всі пристрої зайняті, і черга повна.
    protected int failure;
    // Сумарний час зайнятості всіх пристроїв: два зайняті пристрої за 3 одиниці дають 6.
    private double busyTime;
    // Кожному наступному блоку відповідає імовірність із тим самим індексом.
    // Якщо масиви не задано, використовуємо один успадкований вихід next.
    private Element[] nextElements;
    private double[] probabilities;

    // Конструктор із двома аргументами створює один пристрій, як у попередніх завданнях.
    Process(double meanDelay, int maxQueue) {
        this(meanDelay, maxQueue, 1);
    }

    Process(double meanDelay, int maxQueue, int channelCount) {
        super(meanDelay);
        if (channelCount < 1) {
            throw new IllegalArgumentException("The number of servers must be positive");
        }
        this.maxQueue = maxQueue;
        // Окремий час завершення для кожного пристрою; спочатку всі вільні.
        finishTimes = new double[channelCount];
        for (int i = 0; i < finishTimes.length; i++) {
            finishTimes[i] = Double.POSITIVE_INFINITY;
        }
    }

    // Налаштовуємо будь-яку кількість виходів, зокрема до попередніх блоків.
    public void setNextElements(Element[] nextElements, double[] probabilities) {
        if (nextElements == null || probabilities == null || nextElements.length == 0
                || nextElements.length != probabilities.length) {
            throw new IllegalArgumentException("Routes and probabilities must have equal nonzero lengths");
        }
        double total = 0.0;
        for (int i = 0; i < nextElements.length; i++) {
            if (nextElements[i] == null || !Double.isFinite(probabilities[i])
                    || probabilities[i] < 0.0 || probabilities[i] > 1.0) {
                throw new IllegalArgumentException("Each route needs a target and a probability from 0 to 1");
            }
            total += probabilities[i];
        }
        if (Math.abs(total - 1.0) > 1e-9) {
            throw new IllegalArgumentException("Route probabilities must sum to 1");
        }
        // Копії захищають налаштування від подальшої зміни переданих масивів.
        this.nextElements = nextElements.clone();
        this.probabilities = probabilities.clone();
    }

    private Element chooseNextElement() {
        if (nextElements == null) {
            return next;
        }
        // Наприклад, для [0.7, 0.3]: r < 0.7 вибирає перший блок, решта — другий.
        double r = Math.random();
        double cumulative = 0.0;
        int lastPositive = 0;
        for (int i = 0; i < nextElements.length; i++) {
            if (probabilities[i] > 0.0) {
                lastPositive = i;
            }
            cumulative += probabilities[i];
            if (r < cumulative) {
                return nextElements[i];
            }
        }
        // Захист від похибки додавання дробів; нульову імовірність не вибираємо.
        return nextElements[lastPositive];
    }

    public int getChannelCount() {
        return finishTimes.length;
    }

    @Override
    public void doStatistics(double delta) {
        // Наприклад, за delta = 2 при busyCount = 3 додаємо 6 одиниць роботи.
        busyTime += busyCount * delta;
    }

    public double getMeanLoad() {
        // Середнє завантаження одного пристрою, від 0 до 1.
        // Ділимо на загальний доступний час усіх пристроїв; при time = 0 повертаємо 0.
        return time > 0.0 ? busyTime / (time * getChannelCount()) : 0.0;
    }

    @Override
    public void inAct() {
        // Перебираємо пристрої, щоб знайти перший вільний.
        for (int i = 0; i < finishTimes.length; i++) {
            if (finishTimes[i] == Double.POSITIVE_INFINITY) {
                // Нова заявка починає обслуговування саме на пристрої i.
                finishTimes[i] = time + getDelay();
                busyCount++;
                updateNextTime();
                // Заявка вже прийнята: інші пристрої та чергу не перевіряємо.
                return;
            }
        }
        // Усі пристрої зайняті: додаємо заявку до спільної черги або рахуємо відмову.
        if (queue < maxQueue) {
            queue++;
        } else {
            failure++;
        }
    }

    @Override
    public void outAct() {
        // Знаходимо індекс пристрою, обслуговування на якому завершилося найраніше.
        int channel = getNextChannel();
        quantity++;
        if (queue > 0) {
            // Цей самий пристрій одразу бере заявку з черги, тому busyCount не змінюється.
            queue--;
            finishTimes[channel] = time + getDelay();
        } else {
            // Черга порожня: звільняємо лише цей пристрій.
            finishTimes[channel] = Double.POSITIVE_INFINITY;
            busyCount--;
        }
        // Інші пристрої можуть продовжувати роботу: знаходимо новий найближчий вихід.
        updateNextTime();
        // Спочатку оновлюємо власний стан, потім передаємо заявку в один вибраний блок.
        // При поверненні заявка знову проходить звичайний inAct(): пристрій, черга або відмова.
        chooseNextElement().inAct();
    }

    // Повертаємо індекс мінімального часу. Наприклад, для [8, 5, 9] це індекс 1.
    private int getNextChannel() {
        int channel = 0;
        for (int i = 1; i < finishTimes.length; i++) {
            if (finishTimes[i] < finishTimes[channel]) {
                channel = i;
            }
        }
        return channel;
    }

    // Model бачить один nextTime для всього блока, а не окремі часи пристроїв.
    // Якщо всі вільні, мінімум також нескінченний і подія не планується.
    private void updateNextTime() {
        nextTime = finishTimes[getNextChannel()];
    }
}

/**
 * Вихід із системи. Рахує заявки, які завершили свій маршрут.
 * Власних подій не планує: успадкований nextTime залишається нескінченним.
 */
class Dispose extends Element {
    Dispose() {
        super(0.0);
    }

    @Override
    public void inAct() {
        quantity++;
    }

    @Override
    public void outAct() { }
}

/**
 * Керує модельним часом і виконує події в порядку їх настання.
 */
class Model {
    private final Element[] elements;

    Model(Element... elements) {
        this.elements = elements;
    }

    public void simulate(double endTime) {
        while (true) {
            // Крок 1: шукаємо найближчу подію серед усіх елементів.
            // Строге порівняння < зберігає перший елемент при однакових часах.
            // Порядок пріоритету задано аргументами конструктора Model у main().
            // Локальні змінні пошуку скидаються щоразу; поля element.nextTime зберігаються.
            Element event = null;
            double nextTime = Double.POSITIVE_INFINITY;
            for (Element element : elements) {
                if (element.nextTime < nextTime) {
                    nextTime = element.nextTime;
                    event = element;
                }
            }

            // Крок 2: враховуємо час до наступної події, але не пізніше endTime.
            // Так враховується і останній проміжок, навіть якщо наступна подія вже за межею.
            double statisticsTime = Math.min(nextTime, endTime);
            for (Element element : elements) {
                // Різниця часів — тривалість, протягом якої попередній стан не змінювався.
                element.doStatistics(statisticsTime - element.time);
                element.time = statisticsTime;
            }

            // Крок 3: завершуємо, якщо подій немає або наступна подія пізніша за endTime.
            if (event == null || nextTime > endTime) {
                break;
            }

            // Крок 4: виконуємо одну подію; вона може змінити стан і запланувати наступні.
            // Якщо одночасно завершилися два пристрої, другий обробиться
            // на наступній ітерації з тим самим часом і нульовим приростом статистики.
            event.outAct();
        }
    }
}
